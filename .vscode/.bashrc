[ -f ~/.bashrc ] && source ~/.bashrc
HISTFILE="$PWD/.vscode/.bash_history"
HISTSIZE=10000
HISTFILESIZE=20000
HISTCONTROL=ignoreboth
trap 'history -a' EXIT
echo "Workspace Local Terminal" "$HISTFILE"