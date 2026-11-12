package com.management.referee.web;

import com.management.referee.service.RefereeService;
import com.management.web.ApiVersionConfiguration;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Creates, reads, renames and deletes referees, each kept once, outside any game. */
@RestController
@RequestMapping(RefereeController.PATH)
public class RefereeController {

  public static final String PATH = "/referees";

  private final RefereeService refereeService;

  public RefereeController(RefereeService refereeService) {
    this.refereeService = refereeService;
  }

  @ApiResponse(responseCode = "201", description = "Created")
  @PostMapping
  public ResponseEntity<RefereeResponse> createReferee(
      @Valid @RequestBody RefereeRequest newReferee) {
    RefereeResponse created = RefereeMapper.toResponse(refereeService.create(newReferee.name()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Location", ApiVersionConfiguration.PREFIX + PATH + "/" + created.id())
        .body(created);
  }

  /**
   * A page of referees in name order, those whose name contains {@code name} in any case, or every
   * referee without it.
   */
  @GetMapping
  public PagedModel<RefereeResponse> listReferees(
      @RequestParam(defaultValue = "") String name, @ParameterObject Pageable pageable) {
    return new PagedModel<>(refereeService.list(name, pageable).map(RefereeMapper::toResponse));
  }

  @GetMapping("/{refereeId}")
  public RefereeResponse getReferee(@PathVariable String refereeId) {
    return RefereeMapper.toResponse(refereeService.get(refereeId));
  }

  /**
   * Answers the renamed referee, or 409 with the referee as it now stands if another write won.
   *
   * <p>The 200 is stated because stating any status stops the generator adding its own.
   */
  @ApiResponse(responseCode = "200", description = "OK")
  @ApiResponse(
      responseCode = "409",
      description = "Conflict. Another write won: `current` holds the referee as it now stands",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
  @PutMapping("/{refereeId}")
  public RefereeResponse renameReferee(
      @PathVariable String refereeId, @Valid @RequestBody RefereeRequest rename) {
    return RefereeMapper.toResponse(refereeService.rename(refereeId, rename.name()));
  }

  @ApiResponse(responseCode = "204", description = "No Content")
  @DeleteMapping("/{refereeId}")
  public ResponseEntity<Void> deleteReferee(@PathVariable String refereeId) {
    refereeService.delete(refereeId);
    return ResponseEntity.noContent().build();
  }
}
