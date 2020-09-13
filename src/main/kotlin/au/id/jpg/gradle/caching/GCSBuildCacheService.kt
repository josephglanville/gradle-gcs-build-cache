/**
 * Copyright 2019 Thorsten Ehlers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.id.jpg.gradle.caching

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import org.gradle.caching.*
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.channels.Channels
import java.time.Instant

/**
 * BuildCacheService that stores the build artifacts in Google Cloud Storage.
 * The creation time will be reset in a configurable interval to make sure
 * that artifacts still in use are not deleted.
 *
 * @author Thorsten Ehlers (thorsten.ehlers@googlemail.com) (initial creation)
 */
class GCSBuildCacheService(
    credentials: String,
    private val bucketName: String,
    private val refreshAfterSeconds: Long
) : BuildCacheService {
    private val storage: Storage

    init {
        try {
            val creds = if (credentials.isEmpty())
                GoogleCredentials.getApplicationDefault()
            else ServiceAccountCredentials.fromStream(FileInputStream(credentials))
            storage = StorageOptions.newBuilder().setCredentials(creds).build().service
            storage.get(bucketName) ?: throw BuildCacheException("$bucketName is unavailable")
        } catch (e: FileNotFoundException) {
            throw BuildCacheException("Unable to load credentials from $credentials.", e)
        } catch (e: IOException) {
            throw BuildCacheException("Unable to access Google Cloud Storage bucket '$bucketName'.", e)
        } catch (e: StorageException) {
            throw BuildCacheException("Unable to access Google Cloud Storage bucket '$bucketName'.", e)
        }
    }

    override fun store(key: BuildCacheKey, writer: BuildCacheEntryWriter) = try {
        val blob = BlobInfo.newBuilder(bucketName, key.hashCode).build()
        val outputStream = Channels.newOutputStream(storage.writer(blob))
        writer.writeTo(outputStream)
    } catch (e: StorageException) {
        throw BuildCacheException("Unable to store '${key.hashCode}' in Google Cloud Storage bucket '$bucketName'.", e)
    }

    override fun load(key: BuildCacheKey, reader: BuildCacheEntryReader): Boolean = try {
        storage.get(bucketName, key.hashCode)?.let { blob ->
            reader.readFrom(Channels.newInputStream(blob.reader()))
            if (refreshAfterSeconds > 0) {
                val now = Instant.now()
                // Update creation time so that artifacts that are still used won't be deleted.
                blob.customTime?.let { currentTime ->
                    if (Instant.ofEpochMilli(currentTime).plusSeconds(refreshAfterSeconds).isBefore(now)) {
                        blob.toBuilder().setCustomTime(now.toEpochMilli())
                    }
                }
            }
            true
        } ?: false
    } catch (e: StorageException) {
        // https://github.com/googleapis/google-cloud-java/issues/3402
        if (e.message?.contains("404") == true) false
        else throw BuildCacheException("Unable to load '${key.hashCode}' from Google Cloud Storage bucket '$bucketName'.", e)
    }

    override fun close() {
        // nothing to do
    }
}