package etalii.adp.it;

import java.nio.file.Path;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.kodein.di.Copy;
import org.kodein.di.DI;
import org.kodein.di.bindings.InstanceBinding;
import org.kodein.type.TypeToken;
import org.kodein.type.TypeTokensJVMKt;

import com.intellij.ide.starter.di.DiContainerKt;
import com.intellij.ide.starter.path.GlobalPaths;

import kotlin.Unit;

/**
 * Points the IDE Starter framework at the per-user cache in {@code adp.ideTests.home} before any
 * real-IDE test runs (spec 006 R2). Starter derives every folder it downloads and runs IDEs in from
 * one checkout directory, by default the git repository, so {@code out/ide-tests} would otherwise
 * grow inside the repository. Registered as a launcher session listener, so no test can start an IDE
 * before this runs.
 */
public final class IdeTestsHome implements LauncherSessionListener {

    static final String PROPERTY = "adp.ideTests.home";

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        String home = System.getProperty(PROPERTY);
        if (home == null || home.isBlank()) {
            throw new IllegalStateException(PROPERTY + " is not set; run the real-IDE tests through ./gradlew integrationTest");
        }
        GlobalPaths paths = new GlobalPaths(Path.of(home).toAbsolutePath()) {
        };
        TypeToken<GlobalPaths> type = TypeTokensJVMKt.erased(GlobalPaths.class);
        DI starter = DiContainerKt.getDi();
        DiContainerKt.setDi(DI.Companion.invoke(false, builder -> {
            builder.extend(starter, true, Copy.NonCached.INSTANCE);
            builder.Bind(type, null, true).with(new InstanceBinding<>(type, paths));
            return Unit.INSTANCE;
        }));
    }
}
