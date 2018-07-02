/**
 * Thingsboard OÜ ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2018 Thingsboard OÜ. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of Thingsboard OÜ and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Thingsboard OÜ
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.server.dao.user;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.security.UserCredentials;

public interface UserService {
	
	User findUserById(UserId userId);

	ListenableFuture<User> findUserByIdAsync(UserId userId);

	User findUserByEmail(String email);

	User saveUser(User user);

	UserCredentials findUserCredentialsByUserId(UserId userId);
	
	UserCredentials findUserCredentialsByActivateToken(String activateToken);

	UserCredentials findUserCredentialsByResetToken(String resetToken);

	UserCredentials saveUserCredentials(UserCredentials userCredentials);
	
	UserCredentials activateUserCredentials(String activateToken, String password);
	
	UserCredentials requestPasswordReset(String email);

	void deleteUser(UserId userId);
	
	TextPageData<User> findTenantAdmins(TenantId tenantId, TextPageLink pageLink);
	
	void deleteTenantAdmins(TenantId tenantId);
	
	TextPageData<User> findCustomerUsers(TenantId tenantId, CustomerId customerId, TextPageLink pageLink);

	TextPageData<User> findAllCustomerUsers(TenantId tenantId, TextPageLink pageLink);

	void deleteCustomerUsers(TenantId tenantId, CustomerId customerId);

	EntityView findGroupUser(EntityGroupId entityGroupId, EntityId entityId);

	ListenableFuture<TimePageData<EntityView>> findUsersByEntityGroupId(EntityGroupId entityGroupId, TimePageLink pageLink);

}
