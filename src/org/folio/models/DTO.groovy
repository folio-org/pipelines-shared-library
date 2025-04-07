package org.folio.models

import org.codehaus.groovy.runtime.InvokerHelper

class DTO {

  DTO(){}

  public <T extends DTO> T convertTo(Class<T> classTo){
    T converted = classTo.getDeclaredConstructor().newInstance()

    InvokerHelper.setProperties(converted, properties)

    return converted
  }

  static <Z extends DTO, K extends DTO> Map<? extends String, Z> convertMapTo(Map<? extends String, K> mapToConvert
                                                                  , Class<Z> classTo){
    Map<? extends String, Z> convertedMap = [:]

    mapToConvert.each {key, value ->
      convertedMap.put(key, value.convertTo(classTo))
    }

    return convertedMap
  }
}
