import PropTypes from "prop-types";
import React from "react";
import Form from "react-bootstrap/Form";
import styled from "@emotion/styled";

const FormControl = styled(Form.Control)`
  min-height: 36px;
  resize: none;
  color: hsla(0, 0%, 0%, .6);
  .dark & {
    color: hsla(0, 0%, 95%, .6) !important;
    background-color: var(--dark-secondary) !important;
    box-shadow: var(--dark-box-shadow) !important;
  }
  
  &:-moz-focusring {
    text-shadow: none !important;
  }
  &::placeholder {
    font-size: 15px;
    color: hsla(0, 0%, 0%, .5);
  }
  .dark &::placeholder {
    color: hsl(0, 0%, 49%) !important;
  }
  .dark &:disabled {
    background-color: var(--dark-disabled) !important;
  }
`;

const UiFormControl = (props) => {
    const {label, innerRef, ...otherProps} = props;
    return <FormControl ref={innerRef} required aria-label={label} {...otherProps}/>
};

UiFormControl.propTypes = {
    label: PropTypes.string.isRequired,
};

export {UiFormControl};