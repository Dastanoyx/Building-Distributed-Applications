#!/bin/bash
# Compiles every Java file with no classpath. Dependency errors ("cannot find symbol",
# "package ... does not exist") are expected and ignored; anything that is a PARSE error
# is a real bug in the source and is reported.
cd "$(dirname "$0")/orderflow" || exit 1
find . -name "*.java" > /tmp/all_srcs.txt
javac -proc:none -nowarn -d /tmp/cls_check @/tmp/all_srcs.txt 2>&1 \
  | grep -E "error:" \
  | grep -viE "cannot find symbol|does not exist|static import only|cannot access|symbol:|location:|method does not override|is not abstract|no suitable|incompatible types|bad operand|unreported exception|cannot be applied|has private access|abstract cannot be instantiated|non-static|already defined in|constructor .* in class" \
  > /tmp/syntax_errors.txt
count=$(wc -l < /tmp/syntax_errors.txt)
total=$(wc -l < /tmp/all_srcs.txt)
if [ "$count" -eq 0 ]; then
  echo "OK — $total Java files, no syntax errors"
else
  echo "SYNTAX PROBLEMS ($count):"; cat /tmp/syntax_errors.txt
fi
