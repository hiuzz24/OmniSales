#!/bin/sh
# Kaniko build wrapper — exec thẳng /kaniko/executor với args.
# GitLab Runner exec file này qua `sh -c`, nên args truyền qua $@ là từ dòng script.
exec /kaniko/executor "$@"
