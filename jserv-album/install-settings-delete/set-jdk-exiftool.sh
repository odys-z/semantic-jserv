echo "First download jdk to ~/jserv ."
echo "An cd to ~/jserv, run"
echo "wget https://exiftool.org/Image-ExifTool-13.18.tar.gz"
echo "tar -xvf Image-ExifTool-13.18.tar.gz"
echo "Check https://exiftool.org/index.html for the latest Exiftool version."

export JAVA_HOME=~/jserv/jdk-17.0.12 && export PATH=~/jserv/jdk-17.0.12/bin:~/jserv/image-exiftool:$PATH && java -version && exiftool -ver
