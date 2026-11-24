package ar.edu.utn.frsf.pfc.management.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import tools.jackson.databind.json.JsonMapper;

@RestControllerAdvice(assignableTypes = {WebAccessController.class, WebMachineController.class})
class WebApiErrors {

    private final JsonMapper json;

    WebApiErrors(JsonMapper json) {
        this.json = json;
    }

    @ExceptionHandler(WebProblem.Failure.class)
    void expected(WebProblem.Failure error, HttpServletResponse response) throws IOException {
        error.problem().write(response, json);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    void invalidRequest(HttpServletResponse response) throws IOException {
        WebProblem.INVALID_REQUEST.write(response, json);
    }
}
