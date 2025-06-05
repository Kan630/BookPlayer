cmake_minimum_required(VERSION 3.10.2)

project("bookplayer")

add_library(native-lib SHARED native-lib.cpp)

# Add FFmpeg libs
add_library(avcodec SHARED IMPORTED)
add_library(avformat SHARED IMPORTED)
add_library(avutil SHARED IMPORTED)
add_library(swresample SHARED IMPORTED)
add_library(swscale SHARED IMPORTED)
add_library(avfilter SHARED IMPORTED)
add_library(avdevice SHARED IMPORTED)

add_library(ffmpeg-jni SHARED ffmpeg-jni.c)

set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libavcodec.so)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libavformat.so)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libavutil.so)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libswresample.so)
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libswscale.so)
set_target_properties(avfilter PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libavfilter.so)
set_target_properties(avdevice PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libavdevice.so)

# Include headers
include_directories(${CMAKE_SOURCE_DIR}/include)

find_library(log-lib log)

target_link_libraries(ffmpeg-jni
		native-lib
        avcodec
        avformat
        avutil
        swresample
        swscale
        avfilter
        avdevice
        ${log-lib})
