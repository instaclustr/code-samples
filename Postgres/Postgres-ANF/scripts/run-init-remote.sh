#!/bin/bash -x

set -e

LOG_DIR=$1

JOBS=8
#CLIENTS=16 # CLIENTS is set in a loop further down
DURATION_RO=900
#DURATION_RO=360
#DURATION_RW=3600
DURATION_RW=1800
RUNS_RO=1
RUNS_RW=1

#MOUNTDIR=/mnt/data
#DATADIR=$MOUNTDIR/pgdata

#PGHOST=
#PGUSER=
#PGPASSWORD=
#PGDATABASE=

# for level in 0 1 10 5 6; do
# for level in 1 10 5 6; do
for level in 0; do

	TESTDIR=$LOG_DIR/$level

	if [ -d "$TESTDIR" ]; then
		echo "skipping $TESTDIR"
		continue
	fi

	mkdir -p $TESTDIR

	psql postgres -c "select * from pg_settings" > $TESTDIR/settings.log 2>&1

	#for scale in 100 10000; do
	for scale in 10000; do

		dropdb --if-exists test
		createdb test

		mkdir $TESTDIR/$scale

		t1=`date +%s`
		s=`psql -t -A test -c "select extract(epoch from now())"`
		w=`psql -t -A test -c "select pg_current_wal_lsn()"`
		pgbench -i -I dtgp -s $scale test > $TESTDIR/$scale/init.log 2>&1
		d=`psql -t -A test -c "select extract(epoch from now()) - $s"`
		w=`psql -t -A test -c "select pg_wal_lsn_diff(pg_current_wal_lsn(), '$w')"`
		t2=`date +%s`

		echo "$t1;$t2;$scale;init;$d;$w" >> $LOG_DIR/init.csv 2>&1

		t1=`date +%s`
		s=`psql -t -A test -c "select extract(epoch from now())"`
		w=`psql -t -A test -c "select pg_current_wal_lsn()"`
		vacuumdb --freeze --min-xid-age=1 test > $TESTDIR/$scale/vacuum.log 2>&1
		d=`psql -t -A test -c "select extract(epoch from now()) - $s"`
		w=`psql -t -A test -c "select pg_wal_lsn_diff(pg_current_wal_lsn(), '$w')"`
		t2=`date +%s`

		echo "$t1;$t2;$scale;vacuum;$d;$w" >> $LOG_DIR/init.csv 2>&1

		done

	done

done
