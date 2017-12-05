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
package org.sonar.server.measure.live;

import java.util.OptionalDouble;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.issue.Issue;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Metric;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.RuleType;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueGroupDto;
import org.sonar.server.computation.task.projectanalysis.qualitymodel.DebtRatingGrid;
import org.sonar.server.computation.task.projectanalysis.qualitymodel.Rating;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class IssueMetricFormulaFactoryImplTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private IssueMetricFormulaFactoryImpl underTest = new IssueMetricFormulaFactoryImpl();

  @Test
  public void getFormulaMetrics_include_the_dependent_metrics() {
    for (IssueMetricFormula formula : underTest.getFormulas()) {
      assertThat(underTest.getFormulaMetrics()).contains(formula.getMetric());
      for (Metric dependentMetric : formula.getDependentMetrics()) {
        assertThat(underTest.getFormulaMetrics()).contains(dependentMetric);
      }
    }
  }

  @Test
  public void test_violations() {
    run(CoreMetrics.VIOLATIONS).assertThatValueIs(0);
    run(CoreMetrics.VIOLATIONS, newGroup(), newGroup().setCount(4)).assertThatValueIs(5);

    // exclude resolved
    IssueGroupDto resolved = newResolvedIssue(Issue.RESOLUTION_FIXED, Issue.STATUS_RESOLVED);
    run(CoreMetrics.VIOLATIONS, newGroup(), newGroup(), resolved).assertThatValueIs(2);
  }

  private TestContext run(Metric metric, IssueGroupDto... issues) {
    TestContext context = new TestContext();
    IssueMetricFormula formula = underTest.getFormulas().stream()
      .filter(f -> f.getMetric().getKey().equals(metric.getKey()))
      .findFirst()
      .get();
    formula.compute(context, newIssueCounter(issues));
    return context;
  }

  private static IssueCounter newIssueCounter(IssueGroupDto... issues) {
    return new IssueCounterImpl(asList(issues));
  }

  private static IssueGroupDto newGroup() {
    IssueGroupDto dto = new IssueGroupDto();
    // set non-null fields
    dto.setRuleType(RuleType.CODE_SMELL.getDbConstant());
    dto.setCount(1);
    dto.setEffort(0.0);
    dto.setSeverity(Severity.INFO);
    dto.setStatus(Issue.STATUS_OPEN);
    dto.setInLeak(false);
    return dto;
  }

  private static IssueGroupDto newResolvedIssue(String resolution, String status) {
    return newGroup().setResolution(resolution).setStatus(status);
  }

  private static class TestContext implements IssueMetricFormula.Context {
    private Double doubleValue;
    private Rating ratingValue;
    private String stringValue;

    @Override
    public ComponentDto getComponent() {
      throw new UnsupportedOperationException();
    }

    @Override
    public DebtRatingGrid getDebtRatingGrid() {
      throw new UnsupportedOperationException();
    }

    @Override
    public OptionalDouble getValue(Metric metric) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setValue(double value) {
      this.doubleValue = value;
    }

    @Override
    public void setValue(Rating value) {
      this.ratingValue = value;
    }

    @Override
    public void setValue(String value) {
      this.stringValue = value;
    }

    public void assertThatValueIs(double expectedValue) {
      assertThat(doubleValue).isNotNull().isEqualTo(expectedValue);
    }

    public void assertThatValueIs(Rating expectedValue) {
      assertThat(ratingValue).isNotNull().isEqualTo(expectedValue);
    }

    public void assertThatValueIs(String expectedValue) {
      assertThat(stringValue).isNotNull().isEqualTo(expectedValue);
    }
  }
}
