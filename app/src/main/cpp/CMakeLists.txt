cmake_minimum_required(VERSION 3.22.1)
project(craftnode_native LANGUAGES CXX)

add_library(craftnode_native SHARED native_diagnostics.cpp)
target_compile_features(craftnode_native PRIVATE cxx_std_17)
target_compile_options(craftnode_native PRIVATE -Wall -Wextra -Werror)
target_link_libraries(craftnode_native android log)
