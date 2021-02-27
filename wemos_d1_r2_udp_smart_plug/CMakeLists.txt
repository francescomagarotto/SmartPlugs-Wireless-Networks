# cmake_minimum_required(VERSION <specify CMake version here>)
project(wemos_d1_r2_udp_smart_plug)

set(CMAKE_CXX_STANDARD 14)

include_directories(.)

add_executable(wemos_d1_r2_udp_smart_plug
        wemos_d1_r2_udp_smart_plug.ino)
