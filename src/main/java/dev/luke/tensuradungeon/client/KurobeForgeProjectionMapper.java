package dev.luke.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotPayload;
import java.util.List;

final class KurobeForgeProjectionMapper {
    private KurobeForgeProjectionMapper() {}

    static KurobeForgeView from(KurobeForgeSnapshotPayload payload) {
        if (payload == null || !payload.supportedSchema()) {
            throw new IllegalArgumentException("Supported Kurobe Forge snapshot is required");
        }
        List<KurobeForgeView.ServiceState> fallbackServices = payload.services().stream()
            .map(KurobeForgeProjectionMapper::service)
            .toList();
        List<KurobeForgeView.Weapon> weapons = payload.weapons().stream()
            .map(KurobeForgeProjectionMapper::weapon)
            .toList();
        return new KurobeForgeView(
            weapons,
            fallbackServices,
            payload.cores().stream().map(KurobeForgeProjectionMapper::core).toList(),
            KurobeForgeView.Grade.valueOf(payload.certifiedGrade().name()),
            payload.dungeonServicesLocked(),
            KurobeForgeView.Service.valueOf(payload.initialService().name()),
            false,
            payload.status().message(),
            payload.revision()
        );
    }

    private static KurobeForgeView.Weapon weapon(KurobeForgeSnapshotPayload.Weapon weapon) {
        return new KurobeForgeView.Weapon(
            weapon.id(),
            weapon.name(),
            KurobeForgeView.Grade.valueOf(weapon.grade().name()),
            KurobeForgeView.WeaponShape.valueOf(weapon.shape().name()),
            weapon.equipmentEp(),
            weapon.equipmentEpTarget(),
            weapon.conduitRank(),
            weapon.coreName(),
            weapon.coreId(),
            weapon.coreTier(),
            weapon.totalCharge(),
            weapon.naturalCharge(),
            weapon.capacity(),
            weapon.accessibleCapacity(),
            weapon.burstOutput(),
            weapon.flowOutput(),
            weapon.itemId(),
            weapon.inventorySlot(),
            weapon.stackFingerprint(),
            true,
            weapon.services().stream().map(KurobeForgeProjectionMapper::service).toList()
        );
    }

    private static KurobeForgeView.CoreOption core(KurobeForgeSnapshotPayload.CoreOption core) {
        return new KurobeForgeView.CoreOption(
            core.id(),
            core.name(),
            core.satchelSlot(),
            core.tier(),
            core.totalCharge(),
            core.naturalCharge(),
            core.capacity(),
            core.tutorialFree(),
            core.socketCostLabel()
        );
    }

    private static KurobeForgeView.ServiceState service(
        KurobeForgeSnapshotPayload.ServiceState state
    ) {
        return new KurobeForgeView.ServiceState(
            KurobeForgeView.Service.valueOf(state.service().name()),
            state.available(),
            state.costLabel(),
            state.unavailableReason()
        );
    }
}
