# DepBuilder Documentation

This folder contains the documentation for the DepBuilder - dependency building plugin
for Jenkins. This project is using Sphinx with Read The Docs theme.


## Installation

1. Install pip3 

    ```bash
    # Linux (Debian, Ubuntu)
    sudo apt install python3-pip
    ```

2. Install Sphinx

    ```bash
    # Linux (Debian, Ubuntu)
    sudo apt install python3-sphinx
    ```

3. Install read the docs theme

    pip3 install sphinx_rtd_theme

4. Add live-reload (optional)
    
    ```bash
    # works with the latest version 2020.9.1
    pip3 install sphinx-autobuild

    # make sure the sphinx-autobuild is in your path, if not add it:
    export PATH=~/.local/bin:$PATH
    ```

5. Build the docs

    ```bash
    make html
    ```

6. Watch for changes (auto reload)
 
    ```bash
    make live
    ```
