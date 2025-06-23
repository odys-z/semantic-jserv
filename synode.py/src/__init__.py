import fnmatch
import os
import re
import zipfile


def zip2(distzip, resources, exclude_patterns=[]):
    """
    example: zip2('registry-zsu.zip', {"zsu": "registry-deploy/*"}, ['*.zip'])
    :param distzip:
    :param resources:
    :param exclude_patterns:
    :return: None
    """
    def matches_patterns(filename, patterns):
        """
        Check if a filename matches any of the given patterns.

        Args:
            filename (str): The filename to check (e.g., "data.logs").
            patterns (list): List of patterns (e.g., ["*.logs", "*.dat"]).

        Returns:
            bool: True if the filename matches any pattern, False otherwise.
        """
        return any(fnmatch.fnmatch(os.path.basename(filename), pattern) for pattern in patterns)

    with zipfile.ZipFile(distzip, 'w', zipfile.ZIP_DEFLATED) as zipf:
        err = False
        # resources
        for rk, rv in resources.items():
            if "*" in rv:
                count = 0
                srcroot = re.sub('\\*$', '', rv.replace('\\', '/'))
                for pth, _dir, fs in os.walk(srcroot):
                    for file in fs:
                        if not matches_patterns(file, exclude_patterns):
                            file_path = os.path.join(pth, file)
                            relative_path = os.path.relpath(file_path, srcroot)
                            # print(file, file_path, pth, srcroot, relative_path)

                            visited = set()
                            while os.path.islink(file_path):
                                if file_path in visited:
                                    raise ValueError(f"Cycle detected in symbolic links at {relative_path}")
                                visited.add(file_path)
                                print(file_path, '->', os.path.realpath(file_path))
                                file_path = os.path.realpath(file_path)

                            relative_path = os.path.relpath(relative_path)
                            arcname = os.path.join(rk, relative_path)
                            zipf.write(file_path, arcname)
                            count += 1
                            print(f"Added to ZIP: {relative_path} as {arcname}")
                if count == 0:
                    err = True
                    raise FileNotFoundError(f'[ERROR] No files found in {rv}.')
            else:  # Handle single files (jserv.jar and exiftool.exe)
                file = rk if rv == '.' else rv
                if os.path.exists(file):
                    zipf.write(file, rk)
                    print(f"Added to ZIP: {file} as {rk}")
                else:
                    err = True
                    raise FileNotFoundError(f"[ERROR]: Resource '{rk}': '{file}' not found.")

        # for rk, files in vol_files.items():
        #     for file in files:
        #         file = os.path.join(rk, file)
        #         if os.path.exists(file):
        #             zipf.write(file, file)
        #             print(f"Added volume file to ZIP: {file} as {rk}")
        #         else:
        #             err = True
        #             print(f"[ERROR]: volume file '{file}' not found, skipping.", file=sys.stderr)

    # if not os.path.exists(dist_dir):
    # #     os.makedirs(dist_dir, exist_ok=True)
    # distzip = os.path.join(dist_dir, zip)
    # if os.path.isfile(distzip):
    #     os.remove(distzip)
    # os.rename(zip, distzip)

    print(f'Created ZIP file successfully: {distzip}' if not err else 'Errors while making target (creaded zip file)')
