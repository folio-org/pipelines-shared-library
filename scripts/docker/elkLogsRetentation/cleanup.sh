#!/bin/bash

ES_HOST=${ES_HOST:-"http://elasticsearch-master-headless:9200"}
PATTERN=${PATTERN:-"logs-*"}
DAYS=${DAYS:-3}

echo "Starting ELK cleanup with configuration:"
echo "ES_HOST: $ES_HOST"
echo "PATTERN: $PATTERN"
echo "DAYS: $DAYS"

indices=$(curl -s -X GET "$ES_HOST/_cat/indices/$PATTERN?h=index" | grep '^logs-')

for index in $indices; do
  date_part=$(echo $index | grep -o '[0-9]\{4\}\.[0-9]\{2\}\.[0-9]\{2\}')
  if [ -n "$date_part" ]; then
    index_date=$(date -d "${date_part//./-}" +%s)
    cutoff_date=$(date -d "-$DAYS days" +%s)
    if [ "$index_date" -lt "$cutoff_date" ]; then
      echo "Deleting old index: $index (older than $DAYS days)"
      curl -s -X DELETE "$ES_HOST/$index"
      if [ $? -eq 0 ]; then
        echo "Successfully deleted index: $index"
      else
        echo "Failed to delete index: $index"
      fi
    else
      echo "Keeping index: $index (within $DAYS days retention)"
    fi
  fi
done

echo "ELK cleanup completed at $(date)"
