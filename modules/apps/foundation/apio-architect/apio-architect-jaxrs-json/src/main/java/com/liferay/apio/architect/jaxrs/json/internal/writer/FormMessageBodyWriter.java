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

package com.liferay.apio.architect.jaxrs.json.internal.writer;

import static org.osgi.service.component.annotations.ReferenceCardinality.AT_LEAST_ONE;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import com.liferay.apio.architect.error.ApioDeveloperError.MustHaveFormMessageMapper;
import com.liferay.apio.architect.error.ApioDeveloperError.MustHaveProvider;
import com.liferay.apio.architect.form.Form;
import com.liferay.apio.architect.functional.Try;
import com.liferay.apio.architect.functional.Try.Success;
import com.liferay.apio.architect.language.Language;
import com.liferay.apio.architect.message.json.FormMessageMapper;
import com.liferay.apio.architect.request.RequestInfo;
import com.liferay.apio.architect.response.control.Embedded;
import com.liferay.apio.architect.response.control.Fields;
import com.liferay.apio.architect.url.ServerURL;
import com.liferay.apio.architect.wiring.osgi.manager.ProviderManager;
import com.liferay.apio.architect.wiring.osgi.util.GenericUtil;
import com.liferay.apio.architect.writer.FormWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Alejandro Hernández
 */
@Component(
	immediate = true,
	property = "liferay.apio.architect.message.body.writer=true"
)
@Provider
public class FormMessageBodyWriter implements MessageBodyWriter<Success<Form>> {

	public long getSize(
		Success<Form> success, Class<?> aClass, Type type,
		Annotation[] annotations, MediaType mediaType) {

		return -1;
	}

	public boolean isWriteable(
		Class<?> aClass, Type genericType, Annotation[] annotations,
		MediaType mediaType) {

		Try<Class<Object>> classTry =
			GenericUtil.getFirstGenericTypeArgumentFromTypeTry(
				genericType, Try.class);

		return classTry.filter(
			Form.class::equals
		).isSuccess();
	}

	@Override
	public void writeTo(
			Success<Form> success, Class<?> aClass, Type type,
			Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, Object> multivaluedMap,
			OutputStream outputStream)
		throws IOException, WebApplicationException {

		OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
			outputStream, StandardCharsets.UTF_8);

		PrintWriter printWriter = new PrintWriter(outputStreamWriter, true);

		Form form = success.getValue();

		FormMessageMapper formMessageMapper = getFormMessageMapper(
			mediaType, form);

		RequestInfo requestInfo = RequestInfo.create(
			builder -> builder.httpHeaders(
				_httpHeaders
			).httpServletRequest(
				_httpServletRequest
			).serverURL(
				getServerURL()
			).embedded(
				_providerManager.provideOptional(
					_httpServletRequest, Embedded.class
				).orElse(
					__ -> false
				)
			).fields(
				_providerManager.provideOptional(
					_httpServletRequest, Fields.class
				).orElse(
					__ -> string -> true
				)
			).language(
				_providerManager.provideOptional(
					_httpServletRequest, Language.class
				).orElse(
					Locale::getDefault
				)
			).build());

		FormWriter formWriter = FormWriter.create(
			builder -> builder.form(
				form
			).formMessageMapper(
				formMessageMapper
			).requestInfo(
				requestInfo
			).build());

		printWriter.println(formWriter.write());

		printWriter.close();
	}

	/**
	 * Returns the right {@link FormMessageMapper} for the provided {@code
	 * MediaType} that supports writing the provided {@link Form}.
	 *
	 * @param  mediaType the request's {@code MediaType}
	 * @param  form the {@code Form} to write
	 * @return the {@code FormMessageMapper} that writes the {@code Form} in the
	 *         {@code MediaType}
	 */
	protected FormMessageMapper getFormMessageMapper(
		MediaType mediaType, Form form) {

		Stream<FormMessageMapper> stream = _formMessageMappers.stream();

		String mediaTypeString = mediaType.toString();

		return stream.filter(
			bodyWriter ->
				mediaTypeString.equals(bodyWriter.getMediaType()) &&
				bodyWriter.supports(form, _httpHeaders)
		).findFirst(
		).orElseThrow(
			() -> new MustHaveFormMessageMapper(mediaTypeString)
		);
	}

	/**
	 * Returns the server URL, or throws a {@link MustHaveProvider} developer
	 * error.
	 *
	 * @return the server URL
	 */
	protected ServerURL getServerURL() {
		Optional<ServerURL> optional = _providerManager.provideOptional(
			_httpServletRequest, ServerURL.class);

		return optional.orElseThrow(
			() -> new MustHaveProvider(ServerURL.class));
	}

	@Reference(cardinality = AT_LEAST_ONE, policyOption = GREEDY)
	private List<FormMessageMapper> _formMessageMappers;

	@Context
	private HttpHeaders _httpHeaders;

	@Context
	private HttpServletRequest _httpServletRequest;

	@Reference
	private ProviderManager _providerManager;

}