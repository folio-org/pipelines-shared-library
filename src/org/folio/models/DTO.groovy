package org.folio.models

import org.codehaus.groovy.runtime.InvokerHelper

class DTO {

  DTO(){}

  public <T> T convertTo(Class<T> classTo){
    T converted = classTo.getDeclaredConstructor().newInstance()

    InvokerHelper.setProperties(converted, properties)

    return converted
  }

//  static <T, K extends DTO> Map<? extends String, T> convertMapTo(Map<? extends String, K> mapToConvert
//                                                                  , Class<T> classTo){
//    Map<? extends String, T> convertedMap = [:]
//
//    mapToConvert.each {key, value ->
//      convertedMap.put(key, value.convertTo(classTo))
//    }
//
//    return convertedMap
//  }
}
