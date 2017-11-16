/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.issue.ws;

import com.google.common.io.Resources;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.issue.Issue;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.core.util.Uuids;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.IssueFinder;
import org.sonar.server.issue.IssueUpdater;
import org.sonar.server.issue.TransitionService;
import org.sonar.server.issue.webhook.IssueChangeWebhook;
import org.sonar.server.measure.live.DiffOperation;
import org.sonar.server.user.UserSession;

import static com.google.common.collect.ImmutableList.copyOf;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.ACTION_DO_TRANSITION;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.PARAM_ISSUE;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.PARAM_TRANSITION;

public class DoTransitionAction implements IssuesWsAction {

  private final DbClient dbClient;
  private final UserSession userSession;
  private final IssueFinder issueFinder;
  private final IssueUpdater issueUpdater;
  private final TransitionService transitionService;
  private final OperationResponseWriter responseWriter;
  private final System2 system2;
  private final IssueChangeWebhook issueChangeWebhook;

  public DoTransitionAction(DbClient dbClient, UserSession userSession, IssueFinder issueFinder, IssueUpdater issueUpdater, TransitionService transitionService,
    OperationResponseWriter responseWriter, System2 system2, IssueChangeWebhook issueChangeWebhook) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.issueFinder = issueFinder;
    this.issueUpdater = issueUpdater;
    this.transitionService = transitionService;
    this.responseWriter = responseWriter;
    this.system2 = system2;
    this.issueChangeWebhook = issueChangeWebhook;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction(ACTION_DO_TRANSITION)
      .setDescription("Do workflow transition on an issue. Requires authentication and Browse permission on project.<br/>" +
        "The transitions '" + DefaultTransitions.WONT_FIX + "' and '" + DefaultTransitions.FALSE_POSITIVE + "' require the permission 'Administer Issues'.")
      .setSince("3.6")
      .setChangelog(
        new Change("6.5", "the database ids of the components are removed from the response"),
        new Change("6.5", "the response field components.uuid is deprecated. Use components.key instead."))
      .setHandler(this)
      .setResponseExample(Resources.getResource(this.getClass(), "do_transition-example.json"))
      .setPost(true);

    action.createParam(PARAM_ISSUE)
      .setDescription("Issue key")
      .setRequired(true)
      .setExampleValue(Uuids.UUID_EXAMPLE_01);
    action.createParam(PARAM_TRANSITION)
      .setDescription("Transition")
      .setRequired(true)
      .setPossibleValues(DefaultTransitions.ALL);
  }

  @Override
  public void handle(Request request, Response response) {
    userSession.checkLoggedIn();
    String issue = request.mandatoryParam(PARAM_ISSUE);
    try (DbSession dbSession = dbClient.openSession(false)) {
      IssueDto issueDto = issueFinder.getByKey(dbSession, issue);
      SearchResponseData preloadedSearchResponseData = doTransition(dbSession, issueDto, request.mandatoryParam(PARAM_TRANSITION));
      responseWriter.write(issue, preloadedSearchResponseData, request, response);
    }
  }

  private SearchResponseData doTransition(DbSession session, IssueDto issueDto, String transitionKey) {
    DefaultIssue defaultIssue = issueDto.toDefaultIssue();
    String initialStatus = defaultIssue.status();
    IssueChangeContext context = IssueChangeContext.createUser(new Date(system2.now()), userSession.getLogin());
    transitionService.checkTransitionPermission(transitionKey, defaultIssue);
    if (transitionService.doTransition(defaultIssue, context, transitionKey)) {
      String targetStatus = defaultIssue.status();
      Collection<DiffOperation> diffOperations = new ArrayList<>();

      if (initialStatus.equals(Issue.STATUS_OPEN) || initialStatus.equals(Issue.STATUS_CONFIRMED) || initialStatus.equals(Issue.STATUS_REOPENED)) {
        if (targetStatus.equals(Issue.STATUS_RESOLVED) || targetStatus.equals(Issue.STATUS_CLOSED)) {
          diffOperations.add(new DiffOperation(getMetricKeyForRuleType(defaultIssue.type()), -1.0));
        }

      } else if (initialStatus.equals(Issue.STATUS_RESOLVED) || initialStatus.equals(Issue.STATUS_CLOSED)) {
        if (targetStatus.equals(Issue.STATUS_OPEN) || targetStatus.equals(Issue.STATUS_CONFIRMED) || targetStatus.equals(Issue.STATUS_REOPENED)) {
          diffOperations.add(new DiffOperation(getMetricKeyForRuleType(defaultIssue.type()), 1.0));
        }
      }

      SearchResponseData searchResponseData = issueUpdater.saveIssueAndPreloadSearchResponseData(session, defaultIssue, context, null, diffOperations);
      issueChangeWebhook.onChange(
        new IssueChangeWebhook.IssueChangeData(
          searchResponseData.getIssues().stream().map(IssueDto::toDefaultIssue).collect(MoreCollectors.toList(searchResponseData.getIssues().size())),
          copyOf(searchResponseData.getComponents())),
        new IssueChangeWebhook.IssueChange(transitionKey),
        context);
      return searchResponseData;
    }
    return new SearchResponseData(issueDto);
  }

  private static String getMetricKeyForRuleType(RuleType type) {
    // TODO reuse IssueCounter.TYPE_TO_METRIC_KEY
    switch (type) {
      case CODE_SMELL:
        return CoreMetrics.CODE_SMELLS_KEY;
      case BUG:
        return CoreMetrics.BUGS_KEY;
      case VULNERABILITY:
        return CoreMetrics.VULNERABILITIES_KEY;
      default:
        throw new IllegalArgumentException("Unsupported rule type: " + type);
    }

  }
}
