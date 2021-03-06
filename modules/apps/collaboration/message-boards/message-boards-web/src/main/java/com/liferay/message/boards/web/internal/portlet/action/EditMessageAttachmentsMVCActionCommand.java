/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.message.boards.web.internal.portlet.action;

import com.liferay.message.boards.constants.MBMessageConstants;
import com.liferay.message.boards.constants.MBPortletKeys;
import com.liferay.message.boards.service.MBMessageLocalService;
import com.liferay.message.boards.service.MBMessageService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.portlet.JSONPortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCActionCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.upload.UploadPortletRequest;
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.TempFileEntryUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.upload.UploadResponseHandler;

import java.io.InputStream;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Eudaldo Alonso
 */
@Component(
	property = {
		"javax.portlet.name=" + MBPortletKeys.MESSAGE_BOARDS,
		"javax.portlet.name=" + MBPortletKeys.MESSAGE_BOARDS_ADMIN,
		"mvc.command.name=/message_boards/edit_message_attachments"
	},
	service = MVCActionCommand.class
)
public class EditMessageAttachmentsMVCActionCommand
	extends BaseMVCActionCommand {

	protected void addTempAttachment(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		UploadPortletRequest uploadPortletRequest =
			_portal.getUploadPortletRequest(actionRequest);

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		long categoryId = ParamUtil.getLong(uploadPortletRequest, "categoryId");
		String sourceFileName = uploadPortletRequest.getFileName("file");

		try (InputStream inputStream =
				uploadPortletRequest.getFileAsStream("file")) {

			String tempFileName = TempFileEntryUtil.getTempFileName(
				sourceFileName);

			String mimeType = uploadPortletRequest.getContentType("file");

			FileEntry fileEntry = _mbMessageService.addTempAttachment(
				themeDisplay.getScopeGroupId(), categoryId,
				MBMessageConstants.TEMP_FOLDER_NAME, tempFileName, inputStream,
				mimeType);

			JSONObject jsonObject = _multipleUploadResponseHandler.onSuccess(
				uploadPortletRequest, fileEntry);

			JSONPortletResponseUtil.writeJSON(
				actionRequest, actionResponse, jsonObject);
		}
		catch (PortalException pe) {
			JSONObject jsonObject = _multipleUploadResponseHandler.onFailure(
				actionRequest, pe);

			JSONPortletResponseUtil.writeJSON(
				actionRequest, actionResponse, jsonObject);
		}
	}

	protected void deleteAttachment(ActionRequest actionRequest)
		throws PortalException {

		long messageId = ParamUtil.getLong(actionRequest, "messageId");

		String fileName = ParamUtil.getString(actionRequest, "fileName");

		_mbMessageLocalService.deleteMessageAttachment(messageId, fileName);
	}

	protected void deleteTempAttachment(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		UploadPortletRequest uploadPortletRequest =
			_portal.getUploadPortletRequest(actionRequest);

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		long categoryId = ParamUtil.getLong(uploadPortletRequest, "categoryId");
		String fileName = ParamUtil.getString(actionRequest, "fileName");

		JSONObject jsonObject = JSONFactoryUtil.createJSONObject();

		try {
			_mbMessageService.deleteTempAttachment(
				themeDisplay.getScopeGroupId(), categoryId,
				MBMessageConstants.TEMP_FOLDER_NAME, fileName);

			jsonObject.put("deleted", Boolean.TRUE);
		}
		catch (Exception e) {
			jsonObject.put("deleted", Boolean.FALSE);

			String errorMessage = themeDisplay.translate(
				"an-unexpected-error-occurred-while-deleting-the-file");

			jsonObject.put("errorMessage", errorMessage);
		}

		JSONPortletResponseUtil.writeJSON(
			actionRequest, actionResponse, jsonObject);
	}

	@Override
	protected void doProcessAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		String cmd = ParamUtil.getString(actionRequest, Constants.CMD);

		try {
			if (cmd.equals(Constants.ADD_TEMP)) {
				addTempAttachment(actionRequest, actionResponse);
			}

			if (cmd.equals(Constants.DELETE)) {
				deleteAttachment(actionRequest);
			}

			if (cmd.equals(Constants.DELETE_TEMP)) {
				deleteTempAttachment(actionRequest, actionResponse);
			}
			else if (cmd.equals(Constants.EMPTY_TRASH)) {
				emptyTrash(actionRequest);
			}
			else if (cmd.equals(Constants.RESTORE)) {
				restoreEntries(actionRequest);
			}

			if (Validator.isNotNull(cmd)) {
				String redirect = ParamUtil.getString(
					actionRequest, "redirect");

				sendRedirect(actionRequest, actionResponse, redirect);
			}
		}
		catch (PrincipalException pe) {
			SessionErrors.add(actionRequest, pe.getClass());

			actionResponse.setRenderParameter(
				"mvcPath", "/message_boards/error.jsp");
		}
	}

	protected void emptyTrash(ActionRequest actionRequest) throws Exception {
		long messageId = ParamUtil.getLong(actionRequest, "messageId");

		_mbMessageService.emptyMessageAttachments(messageId);
	}

	protected void restoreEntries(ActionRequest actionRequest)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		long messageId = ParamUtil.getLong(actionRequest, "messageId");

		String fileName = ParamUtil.getString(actionRequest, "fileName");

		_mbMessageLocalService.restoreMessageAttachmentFromTrash(
			themeDisplay.getUserId(), messageId, fileName);
	}

	@Reference(unbind = "-")
	protected void setMBMessageLocalService(
		MBMessageLocalService mbMessageLocalService) {

		_mbMessageLocalService = mbMessageLocalService;
	}

	@Reference(unbind = "-")
	protected void setMBMessageService(MBMessageService mbMessageService) {
		_mbMessageService = mbMessageService;
	}

	private MBMessageLocalService _mbMessageLocalService;
	private MBMessageService _mbMessageService;

	@Reference(target = "(upload.response.handler=multiple)")
	private UploadResponseHandler _multipleUploadResponseHandler;

	@Reference
	private Portal _portal;

}