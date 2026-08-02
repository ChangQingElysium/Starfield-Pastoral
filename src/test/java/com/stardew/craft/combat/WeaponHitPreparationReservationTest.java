package com.stardew.craft.combat;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponHitPreparationReservationTest {
    @Test
    void uncommittedReservationCanReleaseWithoutConsuming()
            throws ReflectiveOperationException {
        List<String> actions = new ArrayList<>();
        WeaponHitPreparation.Reservation reservation = reservation(
                List.of(() -> actions.add("commit")),
                List.of(
                        () -> actions.add("release-first"),
                        () -> actions.add("release-second")
                )
        );

        reservation.release();
        reservation.release();
        reservation.commit();

        assertEquals(
                List.of("release-second", "release-first"),
                actions
        );
    }

    @Test
    void committedReservationConsumesExactlyOnce()
            throws ReflectiveOperationException {
        List<String> actions = new ArrayList<>();
        WeaponHitPreparation.Reservation reservation = reservation(
                List.of(
                        () -> actions.add("commit-first"),
                        () -> actions.add("commit-second")
                ),
                List.of(() -> actions.add("release"))
        );

        reservation.commit();
        reservation.commit();
        reservation.release();

        assertEquals(
                List.of("commit-first", "commit-second"),
                actions
        );
    }

    private static WeaponHitPreparation.Reservation reservation(
            List<Runnable> commits,
            List<Runnable> releases
    ) throws ReflectiveOperationException {
        Constructor<WeaponHitPreparation.Reservation> constructor =
                WeaponHitPreparation.Reservation.class
                        .getDeclaredConstructor(List.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(commits, releases);
    }
}
