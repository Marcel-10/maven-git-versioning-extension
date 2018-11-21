package me.qoomon.maven.extension.gitversioning;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GitUtil {

    public static Status getStatus(Repository repository) {
        try {
            return Git.wrap(repository).status().call();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    public static Optional<String> getHeadBranch(Repository repository) throws IOException {

        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return Optional.of(Constants.MASTER);
        }

        if (ObjectId.isId(repository.getBranch())) {
            return Optional.empty();
        }

        return Optional.ofNullable(repository.getBranch());
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
        try {
            final List<Ref> tags = repository.getRefDatabase().getRefsByPrefix("refs/tags/");
            return tags.isEmpty() ? null : tags.get(tags.size() - 1).getName().replaceFirst(Pattern.quote("refs/tags/"), "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
