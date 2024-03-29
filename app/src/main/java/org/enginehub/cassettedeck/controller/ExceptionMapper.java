/*
 * Copyright (c) EngineHub <https://enginehub.org>
 * Copyright (c) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.enginehub.cassettedeck.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.enginehub.cassettedeck.exception.DownloadException;
import org.enginehub.cassettedeck.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

@ControllerAdvice
@ResponseBody
public class ExceptionMapper {
    private static final Logger LOGGER = LogManager.getLogger();

    // Catch-all
    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handle(Throwable e) {
        LOGGER.warn("Error handling request", e);
        return Map.of("code", "internal.server.error");
    }

    // Client problems
    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handle(MissingServletRequestParameterException e) {
        return Map.of(
            "code", "bad.request",
            "missing-param", e.getParameterName()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handle(HttpMediaTypeNotSupportedException e) {
        return Map.of(
            "code", "bad.request",
            "unsupported.content-type", String.valueOf(e.getContentType())
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public Map<String, Object> handle(HttpMediaTypeNotAcceptableException e) {
        return Map.of(
            "code", "not.acceptable",
            "accepted.content-types", e.getSupportedMediaTypes()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handle(HttpMessageNotReadableException e) {
        return Map.of(
            "code", "bad.request"
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handle(NotFoundException e) {
        return Map.of("code", e.type() + ".not.found");
    }

    // Upstream problems

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handle(DownloadException e) {
        LOGGER.warn("Download error occurred", e);
        return Map.of("code", e.kind().code());
    }
}
