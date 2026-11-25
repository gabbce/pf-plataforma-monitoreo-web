package ar.edu.utn.frsf.pfc.management.station;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import ar.edu.utn.frsf.pfc.management.ManagementApplication;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class StationCredentialCommand {

    private static final String PROVISION = "provision-station";
    private static final String REVOKE = "revoke-station";
    private static final int UNIX_FILE_TYPE_MASK = 0170000;
    private static final int UNIX_CHARACTER_DEVICE = 0020000;

    private StationCredentialCommand() {
    }

    public static boolean matches(String[] args) {
        return args.length > 0 && (PROVISION.equals(args[0]) || REVOKE.equals(args[0]));
    }

    public static int run(String[] args) {
        try {
            System.out.println(execute(args));
            return 0;
        } catch (StationCredentials.Rejected failure) {
            System.err.println(failure.getMessage());
        } catch (IOException failure) {
            System.err.println("Cannot read the Station token from standard input");
        } catch (RuntimeException failure) {
            System.err.println("Station credential command failed");
        }
        return 1;
    }

    private static StationCredentials.Outcome execute(String[] args) throws IOException {
        boolean provision = PROVISION.equals(args[0]);
        if (provision && args.length < 4) {
            throw new StationCredentials.Rejected("Usage: provision-station <chassis> <producerId> <operation> [operation...]");
        }
        if (!provision && args.length != 3) {
            throw new StationCredentials.Rejected("Usage: revoke-station <chassis> <producerId>");
        }
        var instanceId = args[1];
        var producerId = args[2];
        StationCredentials.requireIdentity(instanceId, producerId);
        var operations = provision ? parseOperations(args) : Set.<StationOperation>of();
        var token = provision ? readToken() : null;
        try (var context = new SpringApplicationBuilder(ManagementApplication.class)
                .web(WebApplicationType.NONE)
                .run()) {
            var credentials = context.getBean(StationCredentials.class);
            return provision ? credentials.provision(instanceId, producerId, operations, token)
                    : credentials.revoke(instanceId, producerId);
        } finally {
            if (token != null) {
                Arrays.fill(token, (byte) 0);
            }
        }
    }

    private static Set<StationOperation> parseOperations(String[] args) {
        var operations = EnumSet.noneOf(StationOperation.class);
        for (int index = 3; index < args.length; index++) {
            StationOperation operation;
            try {
                operation = StationOperation.valueOf(args[index]);
            } catch (IllegalArgumentException failure) {
                throw new StationCredentials.Rejected("Unknown Station operation");
            }
            if (!operations.add(operation)) {
                throw new StationCredentials.Rejected("Duplicate Station operation");
            }
        }
        return operations;
    }

    private static byte[] readToken() throws IOException {
        if (hasInteractiveInput()) {
            throw new StationCredentials.Rejected("Supply the Station token through a pipe or redirected file");
        }
        var length = StationToken.LENGTH;
        var input = new byte[length + 3];
        try (var standardInput = new FileInputStream(FileDescriptor.in)) {
            var read = standardInput.readNBytes(input, 0, input.length);
            boolean validEnding = switch (read - length) {
                case 0 -> true;
                case 1 -> input[length] == '\n';
                case 2 -> input[length] == '\r' && input[length + 1] == '\n';
                default -> false;
            };
            if (validEnding) {
                var token = Arrays.copyOf(input, length);
                if (StationToken.isValid(token)) {
                    return token;
                }
                Arrays.fill(token, (byte) 0);
            }
            throw new StationCredentials.Rejected("A Station token must contain 64 lowercase hexadecimal characters");
        } finally {
            Arrays.fill(input, (byte) 0);
        }
    }

    private static boolean hasInteractiveInput() throws IOException {
        var console = System.console();
        if (console != null && console.isTerminal()) {
            return true;
        }

        // On Linux, detect terminal stdin even when redirected stdout hides the console from the JDK.
        var standardInput = Path.of("/proc/self/fd/0");
        if (Files.exists(standardInput)) {
            int mode = (int) Files.getAttribute(standardInput, "unix:mode");
            return (mode & UNIX_FILE_TYPE_MASK) == UNIX_CHARACTER_DEVICE;
        }
        return false;
    }
}
