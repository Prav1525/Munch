cmake_minimum_required(VERSION 3.22.1)
project(munchllm LANGUAGES C CXX)

# llama.cpp inference is extremely slow in Android debug builds when native
# code is compiled at -O0. Keep inference usable in both debug and release APKs.
add_compile_options(-O2)

include(FetchContent)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# Enable Arm runtime-dispatched quantized CPU microkernels on arm64 Android.
set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG master
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(munchllm SHARED munch_llm.cpp)
target_compile_features(munchllm PRIVATE cxx_std_17)
target_compile_options(munchllm PRIVATE -O2)
target_include_directories(munchllm PRIVATE ${llama_cpp_SOURCE_DIR}/include ${llama_cpp_SOURCE_DIR}/ggml/include)
find_library(log-lib log)
target_link_libraries(munchllm PRIVATE llama ${log-lib})
