#!/bin/bash
for i in $(cat resources.txt); do
  terraform state rm "$i"
done