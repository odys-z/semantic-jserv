# Bash functions of the Linux closed tests, sourced by .github/actions/linux-synodes and
# .github/workflows/synodes-linux-upgrade-test.yml. The counterpart of windows_nodes.sh.
#
# Expects CONFIG, WORK, CENTRAL_DOMAIN, CENTRAL_PSWD, and the answer files answers/linux/<node>.txt.
# The synodes run as systemd services, installed by synode-cli's last step (synodepy3
# systemd_units.install_linusrvs(): passwordless sudo on the runner), named after the synode id.

# wait_port <port> <seconds>
wait_port() {
  timeout $2 bash -c "until nc -z 127.0.0.1 $1; do sleep 2; done"
}

# services <node>: prints "<synode-unit> <web-unit>", from settings.json envars linusrv.synode / linusrv.web
services() {
  python $CONFIG/closed_test.py service-names $WORK/$1
}

# check_services <node> <jserv-port>: the node's units are active, and its jserv listens
check_services() {
  local srv websrv s
  read -r srv websrv <<< "$(services $1)"
  [ -n "$srv" ] || { echo "::error::$1: no systemd service in settings.json (envars linusrv.synode)"; return 1; }
  for s in $srv $websrv; do
    systemctl is-active --quiet "$s" || {
      systemctl status "$s" --no-pager -l | tail -20
      echo "::error::$1: service $s is not active"; return 1; }
  done
  wait_port $2 120 || {
    sudo journalctl -u "$srv" --no-pager | tail -80
    echo "::error::$1: jserv not listening on $2"; return 1; }
  echo "$1 is up on $2 ($srv, $websrv)"
}

# check_central: the central still listens
check_central() {
  wait_port 1991 10 && return 0
  echo "::error::the central is not listening on 1991 any more"
  sudo journalctl -u regist-central --no-pager | tail -40
  return 1
}

# setup_node <node> <jserv-port> <expected synode id>
#   synode-cli, fed by answers/linux/<node>.txt, ends by installing and starting the 2 services.
setup_node() {
  check_central || return 1
  # the test central's admin password, not the package's default (else synode-cli asks for it)
  python $CONFIG/closed_test.py central-pswd $WORK/$1/WEB-INF/settings.json "$CENTRAL_PSWD"

  python $CONFIG/feed_answers.py $CONFIG/answers/linux/$1.txt --cwd $WORK/$1 --log $WORK/logs/$1-cli.log
  [ -x $WORK/$1/jre17/bin/java ] || { echo "::error::$1: setup did not finish, see $1-cli.log"; return 1; }
  python $CONFIG/closed_test.py installed $WORK/$1 $CENTRAL_DOMAIN $3 || return 1
  check_services $1 $2 || { echo "::error::$1: see $1-cli.log"; return 1; }
}

# upgrade_node <node> <jserv-port> <expected synode id> <package.tar.gz>
#   synode-upgrade-srv <tar.gz> in the installation folder (synodepy3 commands.update_srv()): stops the
#   2 units, backs up the data and settings, unpacks the package over the folder, copies the backup
#   back, points the units to the jars in the new bin/resource.json, restarts them, and installs the
#   package's bin/synode_py3-*.whl, the upgrade tool itself. It must not ask anything: stdin is
#   /dev/null, so a prompt would fail the upgrade.
upgrade_node() {
  local before after
  check_central || return 1
  before=$(services $1)
  # the installed tool upgrades unattended since the packages with bin/resource.json; an older one
  # reinstalls the units and asks to start them, which can't be answered here
  [ -f $WORK/$1/bin/resource.json ] || {
    echo "::error::$1: the installed package has no bin/resource.json: its synode-upgrade-srv predates the unattended upgrade (it asks to start the services). Install a package built since (release_tag)."
    return 1; }
  ( cd $WORK/$1 && synode-upgrade-srv "$4" < /dev/null ) 2>&1 | tee $WORK/logs/$1-upgrade.log
  grep -q 'Update complete' $WORK/logs/$1-upgrade.log || {
    echo "::error::$1: synode-upgrade-srv did not complete, see $1-upgrade.log"; return 1; }

  python $CONFIG/closed_test.py upgraded $WORK/$1 $WORK/logs/$1-upgrade.log || return 1   # backed up, copied back
  python $CONFIG/closed_test.py installed $WORK/$1 $CENTRAL_DOMAIN $3 || return 1
  after=$(services $1)
  [ "$after" = "$before" ] || { echo "::error::$1: services changed: '$before' -> '$after'"; return 1; }
  python $CONFIG/closed_test.py resources-applied $WORK/$1 || return 1        # the new jars
  check_services $1 $2 || return 1
  python $CONFIG/closed_test.py upgrade-tool "$4" || return 1                  # the new synode_py3
  systemctl cat "${after%% *}" > $WORK/logs/$1-service-after-upgrade.txt || true
}

# refuse_node <node> <jserv-port> <package.tar.gz>
#   synode-upgrade-srv with a package whose bin/resource.json doesn't accept the installed version
#   (closed_test.py repack): it must refuse before changing anything, i.e. no backup-*, services up.
refuse_node() {
  local n0 n1
  n0=$(ls -d $WORK/$1/backup-* 2>/dev/null | wc -l)
  ( cd $WORK/$1 && synode-upgrade-srv "$3" < /dev/null ) 2>&1 | tee $WORK/logs/$1-refused.log
  grep -q 'Upgrade refused' $WORK/logs/$1-refused.log || {
    echo "::error::$1: synode-upgrade-srv did not refuse the package, see $1-refused.log"; return 1; }
  n1=$(ls -d $WORK/$1/backup-* 2>/dev/null | wc -l)
  [ "$n1" = "$n0" ] || { echo "::error::$1: a refused upgrade made a backup"; return 1; }
  check_services $1 $2
}

# node_logs: the services' journals and settings of hub, prv1, prv2 into $WORK/logs
node_logs() {
  local n s
  for n in hub prv1 prv2; do
    cp $WORK/$n/WEB-INF/settings.json $WORK/logs/$n-settings.json 2>/dev/null || true
    [ -f $WORK/$n/WEB-INF/settings.json ] || continue
    for s in $(services $n); do
      sudo journalctl -u "$s" --no-pager > $WORK/logs/$n-$s.log 2>&1 || true
    done
  done
}
