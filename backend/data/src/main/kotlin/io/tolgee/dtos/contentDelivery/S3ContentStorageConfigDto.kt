package io.tolgee.dtos.contentDelivery

import io.tolgee.model.contentDelivery.S3Config
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

class S3ContentStorageConfigDto : S3Config {
  @field:NotBlank
  @field:Size(max = 255)
  override var bucketName: String = ""

  @field:NotBlank
  @field:Size(max = 255)
  override var accessKey: String? = ""

  @field:NotBlank
  @field:Size(max = 255)
  override var secretKey: String? = ""

  @field:NotBlank
  @field:Size(max = 255)
  override var endpoint: String = ""

  @field:NotBlank
  @field:Size(max = 255)
  override val signingRegion: String = ""
}
