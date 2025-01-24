echo "Usage: make install node-id volume-path(e.g. /var/local/volume)"
echo "For Windows, run this in VS Code. (Needing the 'sed' command)"
echo building $1
echo configurations:
ls -l install-settings/$1

mkdir $2
cp install-settings/$1/dictionary.json $2
cp install-settings/$1/syntity.json $2

cp install-settings/$1/settings.json WEB-INF/settings.json

#	@sed -i 's/"volume"\s*:\s*".*"/"volume"   : "$(volume)"/' WEB-INF/settings.json

# realpath $2
vpath=$(realpath $2)
echo Volume is mounted to:
echo $vpath
# sed -i "s/\"volume\"\s*:\s*\".*\"/\"volume\"   : \"$vpath\"/" WEB-INF/settings.json
touch $2/doc-jserv.db $2/jserv-main.db
sed -i "s@\"volume\"\s*:\s*\".*\"@\"volume\"   : \"$vpath\"@" WEB-INF/settings.json
