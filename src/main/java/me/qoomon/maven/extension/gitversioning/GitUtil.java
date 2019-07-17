package me.qoomon.maven.extension.gitversioning;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class GitUtil {

    public static Status getStatus(Repository repository) {
        try {
            return Git.wrap(repository).status().call();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getHeadBranch(Repository repository) throws IOException {

        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return Constants.MASTER;
        }

        if (ObjectId.isId(repository.getBranch())) {
            return null;
        }

        return repository.getBranch();
    }

    public static List<String> getHeadTags(Repository repository) throws IOException {

        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return Collections.emptyList();
        }

        return repository.getRefDatabase().getRefsByPrefix(Constants.R_TAGS).stream()
                .map(ref -> {
                    try {
                        return repository.getRefDatabase().peel(ref);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(ref -> {
                    ObjectId objectId;
                    if (ref.getPeeledObjectId() != null) {
                        objectId = ref.getPeeledObjectId();
                    } else {
                        objectId = ref.getObjectId();
                    }
                    return objectId.equals(head);
                })
                .map(ref -> ref.getName().replaceFirst("^" + Constants.R_TAGS, ""))
                .collect(Collectors.toList());
    }

    public static String getHeadCommit(Repository repository) throws IOException {

        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return "0000000000000000000000000000000000000000";
        }
        return head.getName();
    }

    public static String getLastTag(Repository repository) {
        String tag = null;

        try {
            Git git = Git.wrap(repository);

            Iterable<RevCommit> revCommits = git.log().call();

            for (RevCommit commit : revCommits) {
                Map<ObjectId, String> namedCommits = git.nameRev().addPrefix("refs/tags/").add(commit).call();
                if (namedCommits.containsKey(commit.getId())) {
                    tag = namedCommits.get(commit.getId());
                    if (!tag.contains("^") && !tag.contains("~")) {
                        break;
                    }
                    tag = null;
                }
            }
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException(e);
        }

        return tag;
    }

    public static String getLastTagDescribe(Repository repository, String lastTag) {
        return getTagDescribe(repository, lastTag + "*");
    }

    public static String getTagDescribe(Repository repository, String tag) {
        try {
            Git git = Git.wrap(repository);
            return git.describe().setLong(true).setTags(true).setMatch(tag).call();
        } catch (GitAPIException | InvalidPatternException e) {
            throw new RuntimeException(e);
        }
    }
}
