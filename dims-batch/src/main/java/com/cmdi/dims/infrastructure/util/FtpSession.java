/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cmdi.dims.infrastructure.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public class FtpSession implements Closeable {

    private final Log logger = LogFactory.getLog(this.getClass());

    private final FTPClient client;

    private final AtomicBoolean readingRaw = new AtomicBoolean();

    public FtpSession(FTPClient client) {
        Assert.notNull(client, "client must not be null");
        this.client = client;
    }


    public boolean remove(String path) throws IOException {
        Assert.hasText(path, "path must not be null");
        if (!this.client.deleteFile(path)) {
            throw new IOException("Failed to delete '" + path + "'. Server replied with: " + this.client.getReplyString());
        } else {
            return true;
        }
    }

    public FTPFile[] list(String path) throws IOException {
        this.client.enterLocalPassiveMode();
        return this.client.listFiles(path);
    }

    public String[] listNames(String path) throws IOException {
        return this.client.listNames(path);
    }

    public void read(String path, OutputStream fos) throws IOException {
        Assert.hasText(path, "path must not be null");
        Assert.notNull(fos, "outputStream must not be null");
        boolean completed = this.client.retrieveFile(path, fos);
        if (!completed) {
            throw new IOException("Failed to copy '" + path +
                    "'. Server replied with: " + this.client.getReplyString());
        }
        this.logger.info("File has been successfully transferred from: " + path);
    }

    public InputStream readRaw(String source) throws IOException {
        if (!this.readingRaw.compareAndSet(false, true)) {
            throw new IOException("Previous raw read was not finalized");
        }
        InputStream inputStream = this.client.retrieveFileStream(source);
        if (inputStream == null) {
            throw new IOException("Failed to obtain InputStream for remote file " + source + ": "
                    + this.client.getReplyCode());
        }
        return inputStream;
    }

    public boolean finalizeRaw() throws IOException {
        if (!this.readingRaw.compareAndSet(true, false)) {
            throw new IOException("Raw read is not in process");
        }
        if (this.client.completePendingCommand()) {
            int replyCode = this.client.getReplyCode();
            if (this.logger.isDebugEnabled()) {
                this.logger.debug(this + " finalizeRaw - reply code: " + replyCode);
            }
            return FTPReply.isPositiveCompletion(replyCode);
        }
        throw new IOException("completePendingCommandFailed");
    }

    public void write(InputStream inputStream, String path) throws IOException {
        Assert.notNull(inputStream, "inputStream must not be null");
        Assert.hasText(path, "path must not be null or empty");
        boolean completed = this.client.storeFile(path, inputStream);
        if (!completed) {
            throw new IOException("Failed to write to '" + path
                    + "'. Server replied with: " + this.client.getReplyString());
        }
        if (this.logger.isInfoEnabled()) {
            this.logger.info("File has been successfully transferred to: " + path);
        }
    }

    public void append(InputStream inputStream, String path) throws IOException {
        Assert.notNull(inputStream, "inputStream must not be null");
        Assert.hasText(path, "path must not be null or empty");
        boolean completed = this.client.appendFile(path, inputStream);
        if (!completed) {
            throw new IOException("Failed to append to '" + path
                    + "'. Server replied with: " + this.client.getReplyString());
        }
        if (this.logger.isInfoEnabled()) {
            this.logger.info("File has been successfully appended to: " + path);
        }
    }

    public void close() {
        try {
            if (this.readingRaw.get()) {
                if (!finalizeRaw()) {
                    if (this.logger.isWarnEnabled()) {
                        this.logger.warn("Finalize on readRaw() returned false for " + this);
                    }
                }
            }
            this.client.disconnect();
        } catch (Exception e) {
            if (this.logger.isWarnEnabled()) {
                this.logger.warn("failed to disconnect FTPClient", e);
            }
        }
    }

    public boolean isOpen() {
        try {
            this.client.noop();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public void rename(String pathFrom, String pathTo) throws IOException {
        this.client.deleteFile(pathTo);
        boolean completed = this.client.rename(pathFrom, pathTo);
        if (!completed) {
            throw new IOException("Failed to rename '" + pathFrom +
                    "' to " + pathTo + "'. Server replied with: " + this.client.getReplyString());
        }
        if (this.logger.isInfoEnabled()) {
            this.logger.info("File has been successfully renamed from: " + pathFrom + " to " + pathTo);
        }
    }

    public boolean mkdir(String remoteDirectory) throws IOException {
        return this.client.makeDirectory(remoteDirectory);
    }

    public boolean rmdir(String directory) throws IOException {
        return this.client.removeDirectory(directory);
    }


    public boolean exists(String path) throws IOException {
        Assert.hasText(path, "'path' must not be empty");

        String[] names = this.client.listNames(path);
        boolean exists = !ObjectUtils.isEmpty(names);

        if (!exists) {
            String currentWorkingPath = this.client.printWorkingDirectory();
            Assert.state(currentWorkingPath != null,
                    "working directory cannot be determined; exists check can not be completed");

            try {
                exists = this.client.changeWorkingDirectory(path);
            } finally {
                this.client.changeWorkingDirectory(currentWorkingPath);
            }

        }

        return exists;
    }

    public FTPClient getClientInstance() {
        return this.client;
    }

}
