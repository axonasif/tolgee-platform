package io.tolgee.component.machineTranslation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.tolgee.component.machineTranslation.metadata.Metadata
import io.tolgee.service.machineTranslation.MtServiceInfo

data class TranslationParams(
  val text: String,
  val textRaw: String,
  val keyName: String?,
  val sourceLanguageTag: String,
  val targetLanguageTag: String,
  val serviceInfo: MtServiceInfo,
  val metadata: Metadata?,
  val isBatch: Boolean
) {

  val cacheKey: String
    get() = jacksonObjectMapper()
      .writeValueAsString(listOf(text, sourceLanguageTag, targetLanguageTag, serviceInfo, metadata))
}
