/*
 * Copyright 2000-2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.swabra.snapshots.iteration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.swabra.snapshots.SwabraRules;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 14:27:18
 */
public class FileSystemFilesIterator implements FilesIterator {
  private static final Logger LOG = Logger.getLogger(FileSystemFilesIterator.class);
  private static final int MAX_DEPTH = 130;
  @NotNull
  private final File myRootFolder;
  @NotNull private final SwabraRules myRules;
  private Stack<Iterator<File>> myIterators;
  private final boolean myRequiresListing;
  private final boolean myVerboseLogging;

  public FileSystemFilesIterator(@NotNull File rootFolder,@NotNull final SwabraRules rules) {
    myRootFolder = rootFolder;
    myRules = rules;
    myRequiresListing = myRules.requiresListingForDir(rootFolder);
    myVerboseLogging = TeamCityProperties.getBoolean("teamcity.swabra.snapshot.verbose.logging");
  }

  @Nullable
  public FileInfo getNext() throws IOException {
    boolean postProcess = true;
    while (true) {
      if (myIterators == null) {
        myIterators = new Stack<Iterator<File>>();
        return processFolder(myRootFolder, myRules.shouldInclude(myRootFolder.getPath()));
      }
      if (myIterators.size() > MAX_DEPTH) {
        final StringBuilder builder = new StringBuilder();
        int iteratorsCount = myIterators.size();
        for (int i=0; i<iteratorsCount; i++){
          final Iterator<File> iterator = myIterators.get(i);
          if (iterator.hasNext()) {
            builder.append(iterator.next().getAbsolutePath());
          } else {
            builder.append("<Empty iterator>");
          }
          builder.append("\n");
        }
        LOG.warn("Too many entries in depth (" + iteratorsCount + "). Printing the list: \n" + builder.toString());
        throw new IOException("Too many entries in depth. Is there a loop. Current folder depth is more than " + MAX_DEPTH);
      }
      if (myIterators.isEmpty()) {
        return null;
      }
      final Iterator<File> it = myIterators.peek();
      while (it.hasNext()) {
        final File next = it.next();
        boolean shouldInclude = myRules.shouldInclude(next.getPath());
        if ((next.isDirectory() && myRequiresListing) || shouldInclude) {
          if (next.isFile()) {
            return createFileInfo(next);
          } else if (next.isDirectory()) {
            FileInfo processResult = processFolder(next, shouldInclude);
            if (processResult != null)
              return processResult;
            else {
              //return getNext();
              postProcess = false;
              break;
            }
          } else {
            throw new IOException("Failed to read " + next);
          }
        }
      }
      if (postProcess) {
        myIterators.pop();
        if (myIterators.isEmpty()) {
          return null;
        }
      }
      postProcess = true;
    }
  }

  public void skipDirectory(final FileInfo dirInfo) {
    myIterators.pop();
  }

  public void stopIterator() {
    // do nothing
  }

  public boolean isCurrent() {
    return true;
  }

  @Nullable
  private FileInfo processFolder(File folder, boolean createFileInfo) throws IOException{
    if (!folder.exists()){
      return null;
    }
    final File[] files = folder.listFiles();
    if (files == null) {
      throw new IOException("Failed to get folder content for: " + folder);
    }
    final List<File> filesList = Arrays.asList(files);
    Collections.sort(filesList, new Comparator<File>() {
      public int compare(File o1, File o2) {
        final int res = FilesComparator.compareByType(o1.isFile(), o2.isFile());
        return res == 0 ? o1.getName().compareTo(o2.getName()) : res;
      }
    });
    myIterators.push(filesList.iterator());
    if (myVerboseLogging){
      LOG.info(String.format("Processing '%s'. It has %d files and folders", folder.getAbsolutePath(), files.length));
    }
    if (createFileInfo)
      return createFileInfo(folder);
    else
      return null;
  }

  private static FileInfo createFileInfo(File file) {
    return new FileInfo(file.getAbsolutePath(), file.length(), file.lastModified(), file.isFile());
  }
}
