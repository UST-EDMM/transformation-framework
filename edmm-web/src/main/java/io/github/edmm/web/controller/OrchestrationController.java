package io.github.edmm.web.controller;

import javax.validation.Valid;

import io.github.edmm.web.model.TransformationRequest;
import io.github.edmm.web.model.TriggerRequest;
import io.github.edmm.web.service.OrchestrationHandler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/orchestration")
@Tag(name = "orchestration", description = "The orchestration API")
public class OrchestrationController {

    private final OrchestrationHandler orchestrationHandler;

    public OrchestrationController(OrchestrationHandler orchestrationHandler) {
        this.orchestrationHandler = orchestrationHandler;
    }

    @Operation(summary = "Transforms a given multi EDMM model to the selected technologies and creates a workflow.")
    @PostMapping(value = "/transform", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> transform(@Parameter(required = true) @Valid @RequestBody TransformationRequest model) {
        orchestrationHandler.doTransform(model);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Triggers the technology with a given multi id and environment variables")
    @PostMapping(value = "/trigger", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> trigger(@Valid @RequestBody TriggerRequest triggerRequest) {
        orchestrationHandler.prepareExecution(triggerRequest);
        return ResponseEntity.ok().build();
    }

}
