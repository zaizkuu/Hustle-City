#!/bin/bash
# Builds milestone commits one by one and keeps their snapshot renders for the development timelapse.
# usage: TRAILER_WORK=/some/dir tools/trailer/timelapse.sh
S=${TRAILER_WORK:-/tmp/hustle-trailer}
OUT=$S/timelapse
REPO=$(cd "$(dirname "$0")/../.." && pwd)
mkdir -p $OUT
for c in 848fd8d 7ec5fe1 7f4d0f2 f7a78fc 81e2846 de427e6 5a200df 61dfc39; do
  [ -d $OUT/$c ] && [ "$(ls $OUT/$c | wc -l)" -gt 0 ] && continue
  W=$S/tl_$c
  rm -rf $W; git -C $REPO worktree prune
  git -C $REPO worktree add --detach $W $c >/dev/null 2>&1 || { echo "worktree failed $c"; continue; }
  cp $REPO/local.properties $W/ 2>/dev/null
  cd $W
  echo "$(date +%H:%M) building $c"
  nice -n 15 timeout 2400 ./gradlew -q --no-daemon testDebugUnitTest --tests '*RenderSnapshotTest*' --tests '*UiSnapshotTest*' > $OUT/$c.log 2>&1
  mkdir -p $OUT/$c
  cp app/build/snapshots/*.png $OUT/$c/ 2>/dev/null
  echo "$(date +%H:%M) $c: $(ls $OUT/$c | wc -l) snapshots"
  cd /
  git -C $REPO worktree remove --force $W
done
echo DONE
