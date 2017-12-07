/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
import * as React from 'react';
import * as classNames from 'classnames';
import { Link } from 'react-router';
import PlusIcon from '../../../../components/icons-components/PlusIcon';
import Dropdown from '../../../../components/controls/Dropdown';
import QualifierIcon from '../../../../components/shared/QualifierIcon';

interface Props {
  openOnboardingTutorial: () => void;
}

export default class GlobalNavPlus extends React.PureComponent<Props> {
  handleNewProjectClick = (event: React.SyntheticEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    this.props.openOnboardingTutorial();
  };

  render() {
    return (
      <Dropdown>
        {({ onToggleClick, open }) => (
          <li className={classNames('dropdown', { open })}>
            <a className="navbar-plus" href="#" onClick={onToggleClick}>
              <PlusIcon />
            </a>
            <ul className="dropdown-menu dropdown-menu-right">
              <li>
                <a className="js-new-project" href="#" onClick={this.handleNewProjectClick}>
                  <QualifierIcon className="spacer-right" qualifier="TRK" />
                  Analyze new project
                </a>
              </li>
              <li>
                <Link className="js-new-organization" to="/account/organizations/create">
                  Create new organization
                </Link>
              </li>
            </ul>
          </li>
        )}
      </Dropdown>
    );
  }
}
