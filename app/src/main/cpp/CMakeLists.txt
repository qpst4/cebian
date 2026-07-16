cmake_minimum_required(VERSION 3.22.1)

project(slideindex_jieba)

add_library(slideindex_jieba SHARED jieba_jni.cpp)

target_compile_features(slideindex_jieba PRIVATE cxx_std_17)

target_include_directories(slideindex_jieba PRIVATE include)

find_library(log-lib log)

target_link_libraries(slideindex_jieba ${log-lib})
