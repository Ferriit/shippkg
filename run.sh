#!/bin/env bash

kotlinc src/main.kt -include-runtime -d main.jar
java -jar main.jar $@