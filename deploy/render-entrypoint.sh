#!/bin/sh
set -eu

normalize_database_url() {
  raw_url="$1"

  case "$raw_url" in
    jdbc:postgresql://*)
      export DATABASE_URL="$raw_url"
      return
      ;;
    postgresql://*|postgres://*)
      ;;
    *)
      return
      ;;
  esac

  without_scheme="${raw_url#postgresql://}"
  without_scheme="${without_scheme#postgres://}"

  if echo "$without_scheme" | grep -q "@"; then
    credentials="${without_scheme%%@*}"
    host_and_db="${without_scheme#*@}"
    username="${credentials%%:*}"
    password="${credentials#*:}"

    export DATABASE_USERNAME="${DATABASE_USERNAME:-$username}"
    export DATABASE_PASSWORD="${DATABASE_PASSWORD:-$password}"
  else
    host_and_db="$without_scheme"
  fi

  host_port="${host_and_db%%/*}"
  database_and_query="${host_and_db#*/}"
  database="${database_and_query%%\?*}"
  query=""

  if [ "$database_and_query" != "$database" ]; then
    query="?${database_and_query#*\?}"
  fi

  export DATABASE_URL="jdbc:postgresql://${host_port}/${database}${query}"
}

if [ -n "${RENDER_DATABASE_URL:-}" ]; then
  normalize_database_url "$RENDER_DATABASE_URL"
elif [ -n "${DATABASE_URL:-}" ]; then
  normalize_database_url "$DATABASE_URL"
fi

exec java ${JAVA_OPTS:-} -jar /app/app.jar
