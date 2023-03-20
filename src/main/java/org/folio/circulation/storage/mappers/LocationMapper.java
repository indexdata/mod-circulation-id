package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Campus;
import org.folio.circulation.domain.Institution;
import org.folio.circulation.domain.Library;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.ServicePoint;

import io.vertx.core.json.JsonObject;

public class LocationMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public Location toDomain(JsonObject representation) {
    log.info("Inside location toDomain with object representation {} ",representation);
    return new Location(getProperty(representation, "id"),
      getProperty(representation, "name"),
      getProperty(representation, "code"),
      getServicePointIds(representation),
      getPrimaryServicePointId(representation),
      Institution.unknown(getProperty(representation, "institutionId")),
      Campus.unknown(getProperty(representation, "campusId")),
      Library.unknown(getProperty(representation, "libraryId")),
      ServicePoint.unknown(getProperty(representation, "primaryServicePoint")));
  }

  private Collection<UUID> getServicePointIds(JsonObject representation) {
    return getArrayProperty(representation, "servicePointIds")
      .stream()
      .map(String.class::cast)
      .map(UUID::fromString)
      .collect(Collectors.toList());
  }

  private UUID getPrimaryServicePointId(JsonObject representation) {
    return Optional.ofNullable(getProperty(representation, "primaryServicePoint"))
      .map(UUID::fromString)
      .orElse(null);
  }
}
