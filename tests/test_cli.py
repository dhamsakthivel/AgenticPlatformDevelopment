import json

from nse_swing.cli import main


def test_goal_command_shows_the_hundredfold_math(capsys):
    assert main(["goal"]) == 0
    text = capsys.readouterr().out
    assert "100x" in text
    assert "58.5%" in text
    assert "arithmetic, not a forecast." in text
    assert "No order is sent" in text


def test_demo_backtest_is_labelled(capsys):
    assert main(["backtest", "--demo"]) == 0
    text = capsys.readouterr().out
    assert "DEMO DATA" in text
    assert "No order is sent" in text


def test_paper_demo_starts_flat_and_keeps_its_capital(tmp_path, capsys):
    state = tmp_path / "paper.json"
    assert main(["paper", "--demo", "--state", str(state), "--save", str(tmp_path / "out")]) == 0
    first = capsys.readouterr().out
    assert "DEMO DATA" in first
    assert "Closed trades: 0" in first
    assert "Started a model portfolio" in first
    orders = json.loads((tmp_path / "out" / "orders.json").read_text())
    assert orders["sent_to_broker"] is False
    assert json.loads(state.read_text())["capital"] == 100_000

    assert main(["paper", "--demo", "--state", str(state), "--capital", "50000"]) == 0
    second = capsys.readouterr().out
    assert "Keeping the saved capital" in second
    assert json.loads(state.read_text())["capital"] == 100_000
    assert "Closed trades: 0" in second
