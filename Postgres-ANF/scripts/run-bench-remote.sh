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

		mkdir $TESTDIR/$scale

		# read-only runs

		# repurpose r as client count
		for r in 4 8 16 32 64 96; do

			CLIENTS=$r

			# skip read-only on the small scale (fits into shared buffers)
			if [ "$scale" == "100" ]; then
				continue
			fi

			rm -f pgbench_log.*

			mkdir $TESTDIR/$scale/run-ro-$r

			t1=`date +%s`
			st=`psql -t -A test -c "select sum(pg_table_size(oid)) from pg_class where relnamespace = 2200 and relkind = 'r'"`
			si=`psql -t -A test -c "select sum(pg_indexes_size(oid)) from pg_class where relnamespace = 2200 and relkind = 'r'"`
			sd=`psql -t -A test -c "select pg_database_size('test')"`

			echo "before;$st;$si;$sd" > $TESTDIR/$scale/run-ro-$r/sizes.csv 2>&1

			c=`ps ax | grep collect-stats | grep -v grep | wc -l`
			if [ "$c" != "0" ]; then
				ps ax | grep collect-stats
				ps ax | grep collect-stats | awk '{print $1}' | xargs kill > /dev/null 2>&1
			fi

			./collect-stats.sh $DURATION_RO $TESTDIR/$scale/run-ro-$r &

			s=`psql -t -A test -c "select extract(epoch from now())"`
			w=`psql -t -A test -c "select pg_current_wal_lsn()"`

			# get sample of transactions from last run
			pgbench -n -M prepared -S -j $JOBS -c $CLIENTS -T $DURATION_RO -l --sampling-rate=0.01 test > $TESTDIR/$scale/run-ro-$r/pgbench.log 2>&1

			# get tps per time, times 100 dues to --sampling-rate
			cut -d ' ' -f 5 pgbench_log.* | sort | uniq -c | awk '{print $2 " " $1*100}' > $TESTDIR/pgbench-$scale-ro-$r.data
			# create gnuplot input file
			sed -e s/@SCALE@/$scale/g -e s/@CLIENTS@/$r/g -e s/@TYPE@/ro/g -e s/@TYPELONG@/read-only/g pgbench.gnuplot.in > $TESTDIR/pgbench-$scale-ro-$r.gnuplot

			tar -czf $TESTDIR/$scale/run-ro-$r/pgbench_log.tgz pgbench_log.*
			rm -f pgbench_log.*

			d=`psql -t -A test -c "select extract(epoch from now()) - $s"`
			w=`psql -t -A test -c "select pg_wal_lsn_diff(pg_current_wal_lsn(), '$w')"`
			t2=`date +%s`

			tps=`cat $TESTDIR/$scale/run-ro-$r/pgbench.log | egrep '(without initial|excluding connections establishing)' | awk '{print $3}'`

			echo "$t1;$t2;$scale;ro;$r;$tps;$d;$w" >> $LOG_DIR/results-ro.csv 2>&1
			echo "$t1;$t2;$scale;ro;$r;$tps;$d;$w" >> $LOG_DIR/results.csv 2>&1

			st=`psql -t -A test -c "select sum(pg_table_size(oid)) from pg_class where relnamespace = 2200 and relkind = 'r'"`
			si=`psql -t -A test -c "select sum(pg_indexes_size(oid)) from pg_class where relnamespace = 2200 and relkind = 'r'"`
			sd=`psql -t -A test -c "select pg_database_size('test')"`

			echo "after;$st;$si;$sd" >> $TESTDIR/$scale/run-ro-$r/sizes.csv 2>&1

			sleep 60

		done

		# sync before the read-write phase (needs superuser)
		#psql test -c checkpoint > /dev/null 2>&1
		sleep 360

		# read-write runs

		# repurpose r as client count
		for r in r 4 8 16 32 64 96; do

			CLIENTS=$r

			## skip read-write
			#if [ "$scale" == "100" ]; then
			#	continue
			#fi

			rm -f pgbench_log.*

			mkdir $TESTDIR/$scale/run-rw-$r

			t1=`date +%s`
			st=`psql -t -A test -c "select sum(pg_table_size(oid)) from pg_class where relnamespace = 2200 and relkind = 'r'"`
			si=`psql -t -A test -c "select sum(pg_indexes_size(oid)) from pg_class where relnamespace = 2200 and relkind = 'r'"`
			sd=`psql -t -A test -c "select pg_database_size('test')"`

			echo "before;$st;$si;$sd" > $TESTDIR/$scale/run-rw-$r/sizes.csv 2>&1

			c=`ps ax | grep collect-stats | grep -v grep | wc -l`
			if [ "$c" != "0" ]; then
				ps ax | grep collect-stats | awk '{print $1}' | xargs kill > /dev/null 2>&1
			fi

			./collect-stats.sh $DURATION_RW $TESTDIR/$scale/run-rw-$r &

			s=`psql -t -A test -c "select extract(epoch from now())"`
			w=`psql -t -A test -c "select pg_current_wal_lsn()"`

			# get sample of transactions from last run
			pgbench -n -M prepared -N -j $JOBS -c $CLIENTS -T $DURATION_RW -l --sampling-rate=0.01 test > $TESTDIR/$scale/run-rw-$r/pgbench.log 2>&1

			# get tps per time, times 100 dues to --sampling-rate
			cut -d ' ' -f 5 pgbench_log.* | sort | uniq -c | awk '{print $2 " " $1*100}' > $TESTDIR/pgbench-$scale-rw-$r.data
			# create gnuplot input file
			sed -e s/@SCALE@/$scale/g -e s/@CLIENTS@/$r/g -e s/@TYPE@/rw/g -e s/@TYPELONG@/read-write/g pgbench.gnuplot.in > $TESTDIR/pgbench-$scale-rw-$r.gnuplot

			tar -czf $TESTDIR/$scale/run-rw-$r/pgbench_log.tgz pgbench_log.*
			rm -f pgbench_log.*

			d=`psql -t -A test -c "select extract(epoch from now()) - $s"`
			w=`psql -t -A test -c "select pg_wal_lsn_diff(pg_current_wal_lsn(), '$w')"`
			t2=`date +%s`

			tps=`cat $TESTDIR/$scale/run-rw-$r/pgbench.log | egrep '(without initial|excluding connections establishing)' | awk '{print $3}'`

			echo "$t1;$t2;$scale;rw;$r;$tps;$d;$w" >> $LOG_DIR/results-rw.csv 2>&1
			echo "$t1;$t2;$scale;rw;$r;$tps;$d;$w" >> $LOG_DIR/results.csv 2>&1

			st=`psql -t -A test -c "select sum(pg_table_size(oid)) from pg_class where relnamespace = 2200 and relkind = 'r'"`
			si=`psql -t -A test -c "select sum(pg_indexes_size(oid)) from pg_class where relnamespace = 2200 and relkind = 'r'"`
			sd=`psql -t -A test -c "select pg_database_size('test')"`

			echo "after;$st;$si;$sd" >> $TESTDIR/$scale/run-rw-$r/sizes.csv 2>&1

			sleep 60

		done

	done

done
