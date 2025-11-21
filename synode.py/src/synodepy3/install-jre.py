'''
Thanks to Grok!
'''
import platform
import urllib.request
import zipfile
import os
from pathlib import Path

def get_adoptium_jre17_url(download_url: str = None):
    system = platform.system()
    machine = platform.machine()

    if system == "Windows":
        os_name = "windows"
        ext = "zip"
    elif system == "Darwin":
        os_name = "mac"
        ext = "tar.gz"
    elif system == "Linux":
        os_name = "linux"
        ext = "tar.gz"
    else:
        raise RuntimeError("Unsupported OS")

    if machine in ("AMD64", "x86_64"):
        arch = "x64"
    elif machine in ("aarch64", "arm64"):
        arch = "aarch64"
    else:
        raise RuntimeError(f"Unsupported arch: {machine}")

    # Latest Temurin 17 as of Nov 2025
    # version = "17.0.13+11"   # ← change this when updating
    if download_url is None:
        #               https://github.com/adoptium/temurin17-binaries/releases/download/
        #                       jdk-17.0.9%2B9.1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.zip
        #
        #               https://github.com/adoptium/temurin17-binaries/releases/download
        #                      /jdk-17.0.9%2B9/OpenJDK17U-jre_x86-32_windows_hotspot_17.0.9_9.zip
        #               https://github.com/adoptium/temurin17-binaries/releases/download
        #                      /jdk-17.0.9%2B9/OpenJDK17U-jre_arm_linux_hotspot_17.0.9_9.tar.gz
        download_url = 'https://github.com/adoptium/temurin17-binaries/releases/download'

    build, plus = "17.0.9", "9"
    zip_gz = f"OpenJDK17U-jre_{arch}_{os_name}_hotspot_{build}_{plus}.{ext}"
    return f"{download_url}/jdk-{build}%2B{plus}/{zip_gz}"

def download_and_extract(url, target_dir="jre-download"):
    target_dir = Path(target_dir)
    target_dir.mkdir(exist_ok=True)

    filename = url.split("/")[-1]
    zip_path = target_dir / filename

    if not zip_path.exists():
        print(f"Downloading JRE for {platform.system()} {platform.machine()}...")
        urllib.request.urlretrieve(url, zip_path, reporthook=progress_hook)

    print("Extracting...")
    if filename.endswith(".zip"):
        with zipfile.ZipFile(zip_path, 'r') as z:
            z.extractall(target_dir)
    else:
        import tarfile
        with tarfile.open(zip_path, 'r:gz') as t:
            t.extractall(target_dir)

    # Find the actual jre folder (Adoptium extracts to jdk-xxx-jre)
    for root, dirs, _ in os.walk(target_dir):
        if "bin/java" in [os.path.join(root, d, "bin/java") for d in dirs]:
            return Path(root)
    raise RuntimeError("JRE extraction failed")

def progress_hook(blocknum, blocksize, totalsize):
    read = blocknum * blocksize
    if totalsize > 0:
        percent = min(100, read * 100 // totalsize)
        print(f"\rDownloading... {percent}%", end="")

if __name__ == "__main__":
    if not os.path.exists("jre"):
        url = get_adoptium_jre17_url()
        print(url)
        jre_path = download_and_extract(url, target_dir="jre-17-download")
        # On macOS/Adoptium the real folder is inside
        real_jre = next((jre_path / p for p in os.listdir(jre_path) if p.startswith("jdk")), jre_path)
        os.rename(real_jre, "jre")
    # Now launch your app
    os.execv(".", ["jre/bin/java", "-version"])
