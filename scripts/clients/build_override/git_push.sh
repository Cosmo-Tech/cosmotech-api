#!/bin/bash
# ref: https://help.github.com/articles/adding-an-existing-project-to-github-using-the-command-line/
#
# Export GIT_TOKEN for automatic login
# Usage example: ./git_push.sh vcarluer Cosmo-Tech "minor update" "github.com"
set -e

git_user_id=$1
git_organization_id=$2
git_repo_id=$3
release_note=$4
git_host=$5

if [ "$git_host" = "" ]; then
    git_host="$GIT_HOST"
    if [ "$git_host" = "" ]; then
        git_host="github.com"
    fi
    echo "[INFO] No command line input provided. Set \$git_host to $git_host"
fi

if [ "$git_user_id" = "" ]; then
    git_user_id="$GIT_USER_ID"
    echo "[INFO] No command line input provided. Set \$git_user_id to $git_user_id"
fi

if [ "$git_organization_id" = "" ]; then
    git_organization_id="$GIT_ORGANIZATION_ID"
    echo "[INFO] No command line input provided. Set \$git_organization_id to $git_organization_id"
fi

if [ "$git_repo_id" = "" ]; then
    git_repo_id="$GIT_REPO_ID"
    echo "[INFO] No command line input provided. Set \$git_repo_id to $git_repo_id"
fi

if [ "$release_note" = "" ]; then
    release_note="$GIT_RELEASE_NOTE"
    if [ "$release_note" = "" ]; then
        release_note="Minor update"
    fi
    echo "[INFO] No command line input provided. Set \$release_note to $release_note"
fi

# Create the release directory
mkdir -p ../../../release

# Sets the new remote URI
if [ "$GIT_TOKEN" = "" ]; then
    echo "[INFO] \$GIT_TOKEN (environment variable) is not set. Using the git credential in your environment."
    github_uri=https://${git_user_id}@${git_host}/${git_organization_id}/${git_repo_id}.git
else
    github_uri=https://${git_user_id}:${GIT_TOKEN}@${git_host}/${git_organization_id}/${git_repo_id}.git
fi

# Clone remote repository
pushd ../../../release
git clone ${github_uri}
# Delete all files to remove renamed or deleted files
cd ${git_repo_id}
rm -rf *
popd
# Adds the files in the local repository
cp -r ../* ../../../release/${git_repo_id}
pushd ../../../release/${git_repo_id}

# Stages the new files for commit.
git add .

# Commits the tracked changes and prepares them to be pushed to a remote repository.
git commit -m "$release_note"

# Pushes (Forces) the changes in the local repository up to the remote repository
echo "Git pushing to https://${git_host}/${git_organization_id}/${git_repo_id}.git"
git push origin master 2>&1 | grep -v 'To https'

popd
# Cleaning release repository
rm -rf ../../../release
