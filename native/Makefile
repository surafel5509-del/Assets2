# S Engine 2.0 -- host build for the native core test suite.
#
# Builds the platform-independent core (allocators, ECS, scene graph, math, RHI
# contract + NullRHI) against the system compiler so the invariants can be
# checked without an Android device or a GPU.
#
#	make            build and run the tests
#	make build      build only
#	make clean
#
# The Android build is CMake (see CMakeLists.txt); this Makefile deliberately
# covers only the subset that has no platform dependencies.

CXX      ?= g++
CXXFLAGS ?= -std=c++17 -O2 -g -Wall -Wextra -Wpedantic -Wno-unused-parameter
DEPFLAGS := -MMD -MP
OUT      := build-host
TEST_BIN := $(OUT)/se_engine_tests

CORE_SRCS := \
	engine/core/Memory.cpp \
	engine/ecs/World.cpp \
	engine/scene/SceneGraph.cpp \
	engine/render/NullRHI.cpp

CORE_OBJS := $(patsubst %.cpp,$(OUT)/%.o,$(CORE_SRCS))
TEST_OBJ  := $(OUT)/tests/test_engine.o

.PHONY: all build run clean

all: run

build: $(TEST_BIN)

run: $(TEST_BIN)
	@./$(TEST_BIN)

$(TEST_BIN): $(CORE_OBJS) $(TEST_OBJ)
	@mkdir -p $(dir $@)
	$(CXX) $(CXXFLAGS) -o $@ $^

# -MMD/-MP emit a .d per object so editing a header actually rebuilds its
# dependents; without this, a change to Math.h silently reuses a stale object.
$(OUT)/%.o: %.cpp
	@mkdir -p $(dir $@)
	$(CXX) $(CXXFLAGS) $(DEPFLAGS) -c $< -o $@

-include $(CORE_OBJS:.o=.d) $(TEST_OBJ:.o=.d)

clean:
	rm -rf $(OUT)
