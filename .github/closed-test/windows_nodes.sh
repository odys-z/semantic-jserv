# Bash functions of the Windows closed tests (Git Bash on the runner), sourced by
# .github/actions/windows-synodes and .github/workflows/synodes-windows-upgrade-test.yml.
#
# Expects CONFIG, WORK (D:/a/... paths), CENTRAL_DOMAIN, CENTRAL_PSWD, and the answer files
# answers/windows/<node>.txt.

# wait_port <port> <seconds>
wait_port() {
  python -c "
import socket, sys, time
end = time.time() + $2
while time.time() < end:
    try: socket.create_connection(('127.0.0.1', $1), 2).close(); sys.exit(0)
    except OSError: time.sleep(2)
sys.exit(1)"
}

# services <node>: prints "<synode-service> <web-service>", from settings.json envars winsrv.synode / winsrv.web
services() {
  python $CONFIG/closed_test.py service-names $WORK/$1 | tr -d '\r'
}

# check_services <node> <jserv-port>: the node's services are installed, and its jserv listens
check_services() {
  local srv websrv s
  read -r srv websrv <<< "$(services $1)"
  [ -n "$srv" ] || { echo "::error::$1: no Windows service in settings.json (envars winsrv.synode)"; return 1; }
  for s in $srv $websrv; do
    sc.exe query "$s" > /dev/null || { echo "::error::$1: service $s is not installed"; return 1; }
  done
  if ! wait_port $2 60; then
    sc.exe start "$srv" || true      # installed but not started?
    wait_port $2 120 || { sc.exe query "$srv"; echo "::error::$1: jserv not listening on $2"; return 1; }
  fi
  echo "$1 is up on $2 ($srv, $websrv)"
}

# setup_node <node> <jserv-port> <expected synode id>
setup_node() {
  # the test central's admin password, not the package's default (else setup-cli.exe asks for it)
  python $CONFIG/closed_test.py central-pswd $WORK/$1/WEB-INF/settings.json "$CENTRAL_PSWD"

  python $CONFIG/feed_answers.py $CONFIG/answers/windows/$1.txt --console --cwd $WORK/$1 \
         --log $WORK/logs/$1-cli.log setup-cli.exe
  [ -f $WORK/$1/jre17/bin/java.exe ] || { echo "::error::$1: setup did not finish, see $1-cli.log"; return 1; }
  python $CONFIG/closed_test.py installed $WORK/$1 $CENTRAL_DOMAIN $3 || return 1
  check_services $1 $2 || { echo "::error::$1: see $1-cli.log, winsrv-install.log"; return 1; }
}

# upgrade_node <node> <jserv-port> <expected synode id> <upgrade.exe> <package.zip>
#   upgrade.exe <zip> in the installation folder (synodepy3 commands.update_srv()): stops the 2
#   services, backs up the data and settings, unpacks the zip over the folder, restores the backup,
#   restarts the services. It ends with input('Press Enter to quit ...'), answered by the echo.
upgrade_node() {
  local before after
  before=$(services $1)
  ( cd $WORK/$1 && echo | "$4" "$5" ) 2>&1 | tee $WORK/logs/$1-upgrade.log
  grep -q 'Update complete' $WORK/logs/$1-upgrade.log || {
    echo "::error::$1: upgrade.exe did not complete, see $1-upgrade.log"; return 1; }

  python $CONFIG/closed_test.py upgraded $WORK/$1 || return 1                  # backed up and restored
  python $CONFIG/closed_test.py installed $WORK/$1 $CENTRAL_DOMAIN $3 || return 1
  after=$(services $1)
  [ "$after" = "$before" ] || { echo "::error::$1: services changed: '$before' -> '$after'"; return 1; }
  check_services $1 $2 || return 1
  sc.exe qc "${after%% *}" > $WORK/logs/$1-service-after-upgrade.txt || true
}
