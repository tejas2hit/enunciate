/**
 * Copyright © 2006-2016 Web Cohesion (info@webcohesion.com)
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
package com.webcohesion.enunciate.artifacts;

import com.webcohesion.enunciate.Enunciate;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * A file artifact.
 *
 * @author Ryan Heaton
 */
public class FileArtifact extends BaseArtifact {

  private boolean publicArtifact = true;
  private final File file;
  private String description;
  private ArtifactType artifactType;

  public FileArtifact(String module, String id, File file) {
    super(module, id);
    this.file = file;
  }

  /**
   * The name of the artifact.
   *
   * @return The name of the artifact.
   */
  public String getName() {
    return getFile().getName();
  }

  /**
   * The file for this artifact.
   *
   * @return The file for this artifact.
   */
  public File getFile() {
    return file;
  }

  /**
   * Exports this artifact to the specified file.  If this file is a directory,
   * the directory will be zipped up.
   *
   * @param file The file to export to.
   */
  public void exportTo(File file, Enunciate enunciate) throws IOException {
    if (!this.file.exists()) {
      throw new IOException("Unable to export non-existing file " + this.file.getAbsolutePath());
    }

    if (this.file.isDirectory()) {
      if (file.exists() && file.isDirectory()) {
        enunciate.copyDir(this.file, file);
      }
      else {
        enunciate.zip(file, this.file);
      }
    }
    else {
      if (file.exists() && file.isDirectory()) {
        enunciate.copyFile(this.file, new File(file, this.file.getName()));
      }
      else {
        enunciate.copyFile(this.file, file);
      }
    }
  }

  /**
   * The size of the file.
   *
   * @return The size of the file.
   */
  public long getSize() {
    return this.file.length();
  }

  /**
   * The description of this file artifact.
   *
   * @return The description of this file artifact.
   */
  public String getDescription() {
    return description;
  }

  /**
   * The description of this file artifact.
   *
   * @param description The description of this file artifact.
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Whether this file artifact is a public artifact.
   *
   * @return Whether this file artifact is a public artifact.
   */
  public boolean isPublic() {
    return publicArtifact;
  }

  /**
   * Whether this file artifact is a public artifact.
   *
   * @param bundled Whether this file artifact is a public artifact.
   */
  public void setPublic(boolean bundled) {
    this.publicArtifact = bundled;
  }

  /**
   * The artifact type.
   *
   * @return The artifact type.
   */
  public ArtifactType getArtifactType() {
    return artifactType;
  }

  /**
   * The artifact type.
   *
   * @param artifactType The artifact type.
   */
  public void setArtifactType(ArtifactType artifactType) {
    this.artifactType = artifactType;
  }

  /**
   * The modification date of the file.
   *
   * @return The modification date of the file.
   */
  public Date getCreated() {
    return new Date(this.file.lastModified());
  }
}
