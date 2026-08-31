# Screenshots

Framed by `tools/demo/shot.py`: the IDE window alone, rounded, on a ground of
its own. Nothing here is cropped by hand.

```
python3 -m venv /tmp/shotenv && /tmp/shotenv/bin/pip install Pillow pyobjc-framework-Quartz
/tmp/shotenv/bin/python tools/demo/shot.py 01-sessions-and-review
```

It photographs the largest IntelliJ window whose title contains `CanopyDemo`, so
the window does not have to be in front and nothing else on screen ends up in
the shot. `--match` picks a different one, `--width` sets the framed width
(2400 by default), `--pad` the margin around it.

The workspace comes from `tools/demo/seed_demo.py`; the shot list is in
`tools/demo/SCREENSHOTS.md`.

Setting the scenes needs Accessibility rights for whatever runs this (System
Settings → Privacy & Security → Accessibility). With them, a pointer click lands
where it is aimed and the scenes can be driven from a script; a click carrying a
modifier is still ignored, so a range selection has to be made by hand.
