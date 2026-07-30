"""Tests for squadx-client doctor checks."""

from unittest.mock import MagicMock, patch

from squadx_client.cli.doctor import (
    CheckStatus,
    DoctorReport,
    check_llm_keys,
    check_token,
    run_doctor,
)


def test_report_ok_without_fails():
    r = DoctorReport()
    r.add("a", CheckStatus.OK)
    r.add("b", CheckStatus.WARN)
    assert r.ok is True


def test_report_not_ok_with_fail():
    r = DoctorReport()
    r.add("a", CheckStatus.FAIL, "x")
    assert r.ok is False


@patch("squadx_client.cli.doctor.settings")
def test_token_placeholder_fails(mock_settings):
    mock_settings.api_token = "change-me"
    r = DoctorReport()
    check_token(r)
    assert r.checks[0].status == CheckStatus.FAIL


@patch("squadx_client.cli.doctor.settings")
def test_token_ok(mock_settings):
    mock_settings.api_token = "real-token-value-here"
    r = DoctorReport()
    check_token(r)
    assert r.checks[0].status == CheckStatus.OK


@patch("squadx_client.cli.doctor.settings")
def test_llm_keys_openrouter(mock_settings):
    mock_settings.openrouter_api_key = "sk-or-test"
    mock_settings.openai_api_key = None
    mock_settings.anthropic_api_key = None
    mock_settings.google_api_key = None
    mock_settings.default_model = "openrouter/openai/gpt-4o-mini"
    r = DoctorReport()
    check_llm_keys(r)
    assert r.checks[0].status == CheckStatus.OK
    assert "openrouter" in r.checks[0].detail


@patch("squadx_client.cli.doctor.settings")
def test_llm_keys_missing(mock_settings):
    mock_settings.openrouter_api_key = None
    mock_settings.openai_api_key = None
    mock_settings.anthropic_api_key = None
    mock_settings.google_api_key = None
    mock_settings.default_model = "gpt-4o"
    r = DoctorReport()
    check_llm_keys(r)
    assert r.checks[0].status == CheckStatus.FAIL


@patch("squadx_client.cli.doctor.check_daemon_pid")
@patch("squadx_client.cli.doctor.check_egress_modules")
@patch("squadx_client.cli.doctor.check_llm_keys")
@patch("squadx_client.cli.doctor.check_token")
@patch("squadx_client.cli.doctor.check_api")
@patch("squadx_client.cli.doctor.check_images")
@patch("squadx_client.cli.doctor.check_docker")
def test_run_doctor_invokes_checks(*_mocks):
    report = run_doctor()
    assert isinstance(report, DoctorReport)
