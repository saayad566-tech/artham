# Reproducible build image for Artham. Produces app-debug.apk with no host dependencies.
FROM eclipse-temurin:17-jdk-jammy

ARG GRADLE_VERSION=8.9
ARG SDK_PLATFORM=android-34
ARG SDK_BUILD_TOOLS=34.0.0
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV GRADLE_USER_HOME=/opt/gradle-cache

RUN apt-get update && apt-get install -y --no-install-recommends curl unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Android SDK
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -sL -o /tmp/ct.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q /tmp/ct.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/ct.zip \
    && yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --sdk_root=${ANDROID_HOME} --licenses > /dev/null \
    && ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --sdk_root=${ANDROID_HOME} \
       "platforms;${SDK_PLATFORM}" "build-tools;${SDK_BUILD_TOOLS}" "platform-tools"

WORKDIR /app
COPY . .
RUN echo "sdk.dir=${ANDROID_HOME}" > local.properties \
    && chmod +x gradlew \
    && ./gradlew --no-daemon assembleDebug

# docker build -t artham . && docker create --name a artham && \
#   docker cp a:/app/app/build/outputs/apk/debug/app-debug.apk ./artham-debug.apk
