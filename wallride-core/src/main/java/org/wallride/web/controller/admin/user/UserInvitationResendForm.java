/*
 * Copyright 2014 Tagbangers, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wallride.web.controller.admin.user;

import org.wallride.model.UserInvitationResendRequest;

import java.io.Serializable;

@SuppressWarnings("serial")
public class UserInvitationResendForm implements Serializable {

	private String token;

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public UserInvitationResendRequest buildUserInvitationResendRequest() {
		UserInvitationResendRequest.Builder builder = new UserInvitationResendRequest.Builder();
		return builder
				.token(token)
				.build();
	}
}
