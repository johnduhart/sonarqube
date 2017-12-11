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
import { sortBy } from 'lodash';
import { Organization } from '../../../app/types';
import OrganizationAvatar from '../../../components/common/OrganizationAvatar';
import Dropdown from '../../../components/controls/Dropdown';
import DropdownIcon from '../../../components/icons-components/DropdownIcon';
import OrganizationLink from '../../../components/ui/OrganizationLink';
import { translate } from '../../../helpers/l10n';

interface Props {
  organization: Organization;
  organizations: Organization[];
}

export default function OrganizationNavigationHeader({ organization, organizations }: Props) {
  const other = organizations.filter(o => o.key !== organization.key);

  return (
    <div className="navbar-context-header">
      <h1 className="display-inline-block">
        <OrganizationAvatar organization={organization} />
        {other.length ? (
          <Dropdown>
            {({ onToggleClick, open }) => (
              <div className={classNames('organization-switch', 'dropdown', { open })}>
                <a className="dropdown-toggle" href="#" onClick={onToggleClick}>
                  {organization.name}
                  <DropdownIcon className="little-spacer-left" />
                </a>
                <ul className="dropdown-menu">
                  {sortBy(other, org => org.name.toLowerCase()).map(organization => (
                    <li key={organization.key}>
                      <OrganizationLink className="dropdown-item-flex" organization={organization}>
                        <div>
                          <OrganizationAvatar organization={organization} small={true} />
                          <span className="spacer-left">{organization.name}</span>
                        </div>
                        {organization.isAdmin && (
                          <span className="outline-badge spacer-left">{translate('admin')}</span>
                        )}
                      </OrganizationLink>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </Dropdown>
        ) : (
          <span className="spacer-left">{organization.name}</span>
        )}
      </h1>
      {organization.description != null && (
        <div className="navbar-context-description">
          <p className="text-limited text-top" title={organization.description}>
            {organization.description}
          </p>
        </div>
      )}
    </div>
  );
}
