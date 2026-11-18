cmake_minimum_required(VERSION 3.18)
project(nova-native)

if(NOT ANDROID_ABI)
  message(FATAL_ERROR "ANDROID_ABI required")
endif()
if(NOT SHERPA_JNI_DIR)
  message(FATAL_ERROR "SHERPA_JNI_DIR required")
endif()
if(NOT LLAMA_LIB_DIR)
  message(FATAL_ERROR "LLAMA_LIB_DIR required")
endif()
if(NOT LLAMA_INCLUDE_DIR)
  message(FATAL_ERROR "LLAMA_INCLUDE_DIR required")
endif()
if(NOT GGML_INCLUDE_DIR)
  message(FATAL_ERROR "GGML_INCLUDE_DIR required")
endif()

add_library(sherpa-onnx-c-api SHARED IMPORTED)
set_target_properties(sherpa-onnx-c-api PROPERTIES
  IMPORTED_LOCATION "${SHERPA_JNI_DIR}/${ANDROID_ABI}/libsherpa-onnx-c-api.so")

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
  IMPORTED_LOCATION "${SHERPA_JNI_DIR}/${ANDROID_ABI}/libonnxruntime.so")

add_library(nova-tts SHARED nova_tts_jni.c)
target_include_directories(nova-tts PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_link_libraries(nova-tts sherpa-onnx-c-api onnxruntime log)
target_compile_options(nova-tts PRIVATE -O2 -fvisibility=hidden)

add_library(nova-asr SHARED nova_asr_jni.c)
target_include_directories(nova-asr PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_link_libraries(nova-asr sherpa-onnx-c-api onnxruntime log)
target_compile_options(nova-asr PRIVATE -O2 -fvisibility=hidden)

# llama + ggml shared libs produced by the llama.cpp Android build
file(GLOB LLAMA_SOS "${LLAMA_LIB_DIR}/*.so")
add_library(nova-llm SHARED nova_llm_jni.cpp)
target_include_directories(nova-llm PRIVATE ${LLAMA_INCLUDE_DIR} ${GGML_INCLUDE_DIR})
target_compile_options(nova-llm PRIVATE -O2 -fvisibility=hidden -std=c++17)
# Import each llama/ggml .so
foreach(so ${LLAMA_SOS})
  get_filename_component(fn ${so} NAME_WE)
  string(REGEX REPLACE "^lib" "" tname ${fn})
  add_library(${tname} SHARED IMPORTED)
  set_target_properties(${tname} PROPERTIES IMPORTED_LOCATION ${so})
  list(APPEND LLAMA_IMPORTED ${tname})
endforeach()
target_link_libraries(nova-llm ${LLAMA_IMPORTED} log)
