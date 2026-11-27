function init(event) {
    event.npc.setGeckoIdleAnimation("Idle2");
}

function dialogOption(event) {
    if (event.dialog.getId() !== 9) return;
    if (event.option.getSlot() !== 0) return;

    var animation = event.API.createAnimBuilder()
        .thenPlay("Active")
        .thenPlay("Resolution")
        .thenLoop("Idle2");

    event.npc.syncAnimationsForAll(animation);
}
